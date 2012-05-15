package name.benjaminpeter.hyphen;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.io.UnsupportedEncodingException;
import java.lang.UnsatisfiedLinkError;
import java.lang.UnsupportedOperationException;
import java.nio.charset.Charset;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/**
 * @author Benjamin Peter <BenjaminPeter@arcor.de>
 */
public class Hyphen {

	private static Hyphen hyphen = null;

	/**
	 * The native library instance, created by JNA.
	 */
	private HyphenLibrary hsl = null;

	/**
	 * The library file that was loaded.
	 */
	private String libFile;

	/**
	 * The instance of the HunspellManager, looks for the native lib in the
	 * default directories
	 */
	public static Hyphen getInstance() throws UnsatisfiedLinkError,
			UnsupportedOperationException {
		return getInstance(null);
	}

	/**
	 * The instance of the HunspellManager, looks for the native lib in the
	 * directory specified.
	 * 
	 * @param libDir
	 *          Optional absolute directory where the native lib can be found.
	 */
	public static Hyphen getInstance(final String libDir)
			throws UnsatisfiedLinkError, UnsupportedOperationException {
		if (hyphen != null) {
			return hyphen;
		}

		hyphen = new Hyphen(libDir);
		return hyphen;
	}

	protected void tryLoad(final String libFile)
			throws UnsupportedOperationException {
		hsl = (HyphenLibrary) Native.loadLibrary(libFile, HyphenLibrary.class);
	}

	/**
	 * Constructor for the library, loads the native lib.
	 * 
	 * Loading is done in the first of the following three ways that works: 1)
	 * Unmodified load in the provided directory. 2) libFile stripped back to the
	 * base name (^lib(.*)\.so on unix) 3) The library is searched for in the
	 * classpath, extracted to disk and loaded.
	 * 
	 * @param libDir
	 *          Optional absolute directory where the native lib can be found.
	 * @throws UnsupportedOperationException
	 *           if the OS or architecture is simply not supported.
	 */
	protected Hyphen(final String libDir) throws UnsatisfiedLinkError,
			UnsupportedOperationException {

		libFile = libDir != null ? libDir + "/" + libName() : libNameBare();
		try {
			hsl = (HyphenLibrary) Native.loadLibrary(libFile, HyphenLibrary.class);
		} catch (UnsatisfiedLinkError urgh) {

			// Oh dear, the library was not found in the file system, let's try the
			// classpath
			libFile = libName();
			InputStream is = Hyphen.class.getResourceAsStream("/" + libFile);
			if (is == null) {
				throw new UnsatisfiedLinkError("Can't find " + libFile
						+ " in the filesystem nor in the classpath\n" + urgh);
			}

			// Extract the library from the classpath into a temp file.
			File lib;
			FileOutputStream fos = null;
			try {
				lib = File.createTempFile("jna", "." + libFile);
				lib.deleteOnExit();
				fos = new FileOutputStream(lib);
				int count;
				byte[] buf = new byte[1024];
				while ((count = is.read(buf, 0, buf.length)) > 0) {
					fos.write(buf, 0, count);
				}

			} catch (IOException e) {
				throw new Error("Failed to create temporary file for " + libFile, e);
			} finally {
				try {
					is.close();
				} catch (IOException e) {
				}
				if (fos != null) {
					try {
						fos.close();
					} catch (IOException e) {
					}
				}
			}
			System.out.println("Loading temp lib: " + lib.getAbsolutePath());
			hsl = (HyphenLibrary) Native.loadLibrary(lib.getAbsolutePath(),
					HyphenLibrary.class);
		}
	}

	public String getLibFile() {
		return libFile;
	}

	/**
	 * Calculate the filename of the native hunspell lib. The files have
	 * completely different names to allow them to live in the same directory and
	 * avoid confusion.
	 */
	public static String libName() throws UnsupportedOperationException {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.startsWith("windows")) {
			return libNameBare() + ".dll";

		} else if (os.startsWith("mac os x")) {
			// return libNameBare()+".dylib";
			return libNameBare() + ".jnilib";

		} else {
			return "lib" + libNameBare() + ".so";
		}
	}

	public static String libNameBare() throws UnsupportedOperationException {
		String os = System.getProperty("os.name").toLowerCase();
		String arch = System.getProperty("os.arch").toLowerCase();

		// Annoying that Java doesn't have consistent names for the arch types:
		boolean x86 = arch.equals("x86") || arch.equals("i386")
				|| arch.equals("i686");
		boolean amd64 = arch.equals("x86_64") || arch.equals("amd64")
				|| arch.equals("ia64n");

		if (os.startsWith("windows")) {
			if (x86) {
				return "hyphen-win-x86-32";
			}
			if (amd64) {
				return "hyphen-win-x86-64";
			}

		} else if (os.startsWith("mac os x")) {
			if (x86) {
				return "hyphen-darwin-x86-32";
			}
			if (amd64) {
				return "hyphen-darwin-x86-64";
			}
			if (arch.equals("ppc")) {
				return "hyphen-darwin-ppc-32";
			}

		} else if (os.startsWith("linux")) {
			if (x86) {
				return "hyphen-linux-x86-32";
			}
			if (amd64) {
				return "hyphen-linux-x86-64";
			}

		} else if (os.startsWith("sunos")) {
			// if (arch.equals("sparc")) {
			// return "hyphen-sunos-sparc-64";
			// }
		}

		throw new UnsupportedOperationException("Unknown OS/arch: " + os + "/"
				+ arch);
	}

	/**
	 * This is the cache where we keep the already loaded dictionaries around
	 */
	private final HashMap<String, Dictionary> map = new HashMap<String, Dictionary>();

	/**
	 * Gets an instance of the dictionary.
	 * 
	 * @param baseFileName
	 *          the base name of the dictionary, passing /dict/da_DK means that
	 *          the files /dict/da_DK.dic and /dict/da_DK.aff get loaded
	 */
	public Dictionary getDictionary(final String baseFileName)
			throws FileNotFoundException, UnsupportedEncodingException {

		// TODO: Detect if the dictionary files have changed and reload if they
		// have.
		if (map.containsKey(baseFileName)) {
			return map.get(baseFileName);

		} else {
			Dictionary d = new Dictionary(baseFileName);
			map.put(baseFileName, d);
			return d;
		}
	}

	/**
	 * Removes a dictionary from the internal cache
	 * 
	 * @param baseFileName
	 *          the base name of the dictionary, as passed to getDictionary()
	 */
	public void destroyDictionary(final String baseFileName) {
		if (map.containsKey(baseFileName)) {
			map.remove(baseFileName);
		}
	}

	/**
	 * Class representing a single dictionary.
	 */
	public class Dictionary {
		/**
		 * The pointer to the hunspell object as returned by the hunspell
		 * constructor.
		 */
		private Pointer hunspellDict = null;

		/**
		 * The encoding used by this dictionary
		 */
		private final String encoding = "UTF8";

		/**
		 * Creates an instance of the dictionary.
		 * 
		 * @param baseFileName
		 *          the base name of the dictionary,
		 */
		Dictionary(final String baseFileName) throws FileNotFoundException,
				UnsupportedEncodingException {
			File dic = new File(baseFileName);

			if (!dic.canRead()) {
				throw new FileNotFoundException("The dictionary files " + baseFileName
						+ " could not be read");
			}

			hunspellDict = hsl.hnj_hyphen_load(dic.toString());
			// This will blow up if the encoding doesn't exist
			// TODO remove if we know what it actually does
			stringToBytes("test");
		}

		/**
		 * Deallocate the dictionary.
		 */
		public void destroy() {
			if (hsl != null && hunspellDict != null) {
				hsl.hnj_hyphen_free(hunspellDict);
				hunspellDict = null;
			}
		}

		/**
		 * Check if a word is spelled correctly
		 * 
		 * @param word
		 *          The word to check.
		 */
		public String hyphenate(final String word) {
			PointerByReference rep = new PointerByReference();
			PointerByReference pos = new PointerByReference();
			PointerByReference cut = new PointerByReference();

			byte[] asciiWord = word.getBytes(Charset.forName("ISO-8859-1"));
			byte[] hyphens = new byte[asciiWord.length + 1];
			byte[] hyphenated = new byte[asciiWord.length * 2];

			// TODO use return value
			@SuppressWarnings("unused")
			int success = hsl.hnj_hyphen_hyphenate2(hunspellDict, asciiWord,
					asciiWord.length, hyphens, hyphenated, rep, pos, cut);
			return new String(hyphenated, 0, strlen(hyphenated),
					Charset.forName("ISO-8859-1"));
		}

		/*
		 * Determine the size of the string, byte is expected to be a zero
		 * terminated C-string.
		 */
		private int strlen(final byte[] hyphenated) {
			int i = 0;
			while (hyphenated[i] != 0) {
				i++;
			}
			return i;
		}

		/**
		 * Convert a Java string to a zero terminated byte array, in the encoding of
		 * the dictionary, as expected by the hunspell functions.
		 */
		protected byte[] stringToBytes(final String str)
				throws UnsupportedEncodingException {
			return (str + "\u0000").getBytes(encoding);
		}
	}
}
