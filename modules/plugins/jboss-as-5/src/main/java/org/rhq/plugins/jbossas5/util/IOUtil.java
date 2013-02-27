package org.rhq.plugins.jbossas5.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Writer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author young
 */
public final class IOUtil {

    public static final int BUFFER_SIZE = 4096;

    public static void zip(File files[], File zip) throws IOException {
        ZipOutputStream out = null;
        byte[] buf = new byte[BUFFER_SIZE];

        try {
            out = new ZipOutputStream(new FileOutputStream(zip));
            // Compress the files
            for (int i = 0; i < files.length; i++) {
                if (files[i] == null || !files[i].exists())
                    continue;

                FileInputStream in = new FileInputStream(files[i]);

                out.putNextEntry(new ZipEntry(files[i].getName()));

                // Transfer bytes from the file to the ZIP file
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }

                // Complete the entry
                out.closeEntry();
                in.close();
            }

        } finally {
            // Complete the ZIP file
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
        }
    }
    
    /**
     * @param dir
     * @param key
     * @param days
     * @return The latest file in the <code>dir</code>, which was last modified in <days> and with name contained <code>key</code>. Or return null.
     */
    public static File getLastFile(File dir, final String key, Long days){
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (name != null && name.contains(key)) {
                    return true;
                }
                return false;
            }
        });

        File last = null;
        for (File file : files) {
            if (last == null || last.lastModified() < file.lastModified()) {
                last = file;
            }
        }
        if (last != null && last.lastModified() > System.currentTimeMillis() - days) {
            return last;
        }
        return null;
    }
    
    public static void close(Writer writer){
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                //ignore
            }
        }
    }
}
