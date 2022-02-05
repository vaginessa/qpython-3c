package com.quseit.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.support.annotation.RequiresApi;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static android.support.v4.app.ActivityCompat.startActivityForResult;

public class DocumentsUtils {

    private static final String TAG = DocumentsUtils.class.getSimpleName();

    public static final int OPEN_DOCUMENT_TREE_CODE = 8000;
    public static final int MAX_BUFFER_SIZE = 5242880;//max buffer size 5MB

    private static List<String> sExtSdCardPaths = new ArrayList<>();

    private static String requestRootPath = null;

    private DocumentsUtils() {

    }

    public static void cleanCache() {
        sExtSdCardPaths.clear();
    }

    /**
     * Get a list of external SD card paths. (Kitkat or higher.)
     *
     * @return A list of external SD card paths.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static String[] getExtSdCardPaths(Context context) {
        if (sExtSdCardPaths.size() > 0) {
            return sExtSdCardPaths.toArray(new String[0]);
        }
        for (File file : context.getExternalFilesDirs("external")) {
            if (file != null && !file.equals(context.getExternalFilesDir("external"))) {
                int index = file.getAbsolutePath().lastIndexOf("/Android/data");
                if (index < 0) {
                    Log.d(TAG, "Unexpected external file dir: " + file.getAbsolutePath());
                } else {
                    String path = file.getAbsolutePath().substring(0, index);
                    try {
                        path = new File(path).getCanonicalPath();
                    } catch (IOException e) {
                        // Keep non-canonical path.
                    }
                    sExtSdCardPaths.add(path);
                }
            }
        }
        if (sExtSdCardPaths.isEmpty()) sExtSdCardPaths.add("/sdcard");
        return sExtSdCardPaths.toArray(new String[0]);
    }

    /**
     * Determine the main folder of the external SD card containing the given file.
     *
     * @param file the file.
     * @return The main folder of the external SD card containing this file, if the file is on an SD
     * card. Otherwise,
     * null is returned.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static String getExtSdCardFolder(final File file, Context context) {
        String[] extSdPaths = getExtSdCardPaths(context);
        try {
            for (int i = 0; i < extSdPaths.length; i++) {
                if (file.getCanonicalPath().startsWith(extSdPaths[i])) {
                    return extSdPaths[i];
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /**
     * Determine if a file is on external sd card. (Kitkat or higher.)
     *
     * @param file The file.
     * @return true if on external sd card.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean isOnExtSdCard(final File file, Context c) {
        return getExtSdCardFolder(file, c) != null;
    }

    /**
     * Get a DocumentFile corresponding to the given file (for writing on ExtSdCard on Android 5).
     * If the file is not
     * existing, it is created.
     *
     * @param file        The file.
     * @param isDirectory flag indicating if the file should be a directory.
     * @return The DocumentFile
     */
    public static DocumentFile getDocumentFile(final File file, final boolean isDirectory,
                                               Context context) {

        String baseFolder = getExtSdCardFolder(file, context);
        boolean originalDirectory = false;
        if (baseFolder == null) {
            return null;
        }

        String relativePath = null;
        try {
            String fullPath = file.getCanonicalPath();
            if (!baseFolder.equals(fullPath)) {
                relativePath = fullPath.substring(baseFolder.length() + 1);
            } else {
                originalDirectory = true;
            }
        } catch (IOException e) {
            return null;
        } catch (Exception f) {
            originalDirectory = true;
            //continue
        }
        String as = PreferenceManager.getDefaultSharedPreferences(context).getString(baseFolder,
                null);

        Uri treeUri = null;
        if (as != null) treeUri = Uri.parse(as);
        if (treeUri == null) {
            return null;
        }

        // start with root of SD card and then parse through document tree.
        DocumentFile document = DocumentFile.fromTreeUri(context, treeUri);
        if (originalDirectory) return document;
        String[] parts = relativePath.split("/");
        for (int i = 0; i < parts.length; i++) {
            DocumentFile nextDocument = document.findFile(parts[i]);

            if (nextDocument == null) {
                if ((i < parts.length - 1) || isDirectory) {
                    nextDocument = document.createDirectory(parts[i]);
                } else {
                    nextDocument = document.createFile("image", parts[i]);
                }
            }
            document = nextDocument;
        }

        return document;
    }

    public static boolean mkdirs(Context context, File dir) {
        boolean res = dir.mkdirs();
        if (!res) {
            if (DocumentsUtils.isOnExtSdCard(dir, context)) {
                DocumentFile documentFile = DocumentsUtils.getDocumentFile(dir, true, context);
                res = documentFile != null && documentFile.canWrite();
            }
        }
        return res;
    }

    private static boolean FileDelete(File file) {
        if (file.isFile()) return file.delete();
        File subFiles[] = file.listFiles();
        if (subFiles.length==0) return file.delete();
        boolean ret = true;
        for(File subFile:subFiles){
            if (subFile.isDirectory()) ret = FileDelete(subFile) && ret;
            else ret = subFile.delete() && ret;
        }
        return ret && file.delete();
    }

    public static boolean delete(Context context, File file) {
        boolean ret = FileDelete(file);
        if (ret) return ret;
        if (DocumentsUtils.isOnExtSdCard(file, context)) {
            DocumentFile f = DocumentsUtils.getDocumentFile(file, false, context);
            if (f != null) {
                ret = f.delete();
            }
        }
        return ret;
    }

    public static boolean canWrite(File file) {
        boolean res = file.exists() && file.canWrite();

        if (!res && !file.exists()) {
            try {
                if (!file.isDirectory()) {
                    res = file.createNewFile() && file.delete();
                } else {
                    res = file.mkdirs() && file.delete();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return res;
    }

    public static boolean canWrite(Context context, File file) {
        boolean res = canWrite(file);

        if (!res && DocumentsUtils.isOnExtSdCard(file, context)) {
            DocumentFile documentFile = DocumentsUtils.getDocumentFile(file, true, context);
            res = documentFile != null && documentFile.canWrite();
        }
        return res;
    }

    private static boolean renameToCross(Context context,File src,File dest) throws Exception {
        copy(context,src,dest);
        boolean exist = dest.exists();
        if (exist) delete(context,src);
        return exist;
    }

    public static boolean renameTo(Context context, File src, File dest) throws Exception {
        boolean res = src.renameTo(dest);
        if (res) return true;

        if (!res && isOnExtSdCard(dest, context)) {
            DocumentFile srcDoc;
            if (isOnExtSdCard(src, context)) {
                srcDoc = getDocumentFile(src, false, context);
            } else {
                srcDoc = DocumentFile.fromFile(src);
            }
            DocumentFile destDoc = getDocumentFile(dest.getParentFile(), true, context);

            if (srcDoc != null && destDoc != null) {
                    if (src.getParent().equals(dest.getParent())) {
                        res = srcDoc.renameTo(dest.getName());
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        DocumentFile srcParentDoc = srcDoc.getParentFile();
                        if (srcParentDoc!=null) {
                        res = DocumentsContract.moveDocument(context.getContentResolver(),
                                srcDoc.getUri(),
                                srcParentDoc.getUri(),
                                destDoc.getUri()) != null;
                    } else return renameToCross(context, src, dest);
                    }
            } else {
                if ((src.exists() || srcDoc!=null) && (dest.canWrite() || destDoc!=null))
                    return renameToCross(context, src, dest);
                else return false;
                }
        } else return renameToCross(context, src, dest);

        return res;
    }

    public static InputStream getInputStream(Context context, File destFile) {
        InputStream in = null;
        try {
            if (!canWrite(destFile) && isOnExtSdCard(destFile, context)) {
                DocumentFile file = DocumentsUtils.getDocumentFile(destFile, false, context);
                if (file != null && file.canWrite()) {
                    in = context.getContentResolver().openInputStream(file.getUri());
                }
            } else {
                in = new FileInputStream(destFile);

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return in;
    }

    public static OutputStream getOutputStream(Context context, File destFile) {
        OutputStream out = null;
        try {
            if (!canWrite(destFile) && isOnExtSdCard(destFile, context)) {
                DocumentFile file = DocumentsUtils.getDocumentFile(destFile, false, context);
                if (file != null && file.canWrite())
                    out = context.getContentResolver().openOutputStream(file.getUri());
            } else {
                out = new FileOutputStream(destFile);

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return out;
    }

    public static boolean saveTreeUri(Context context, String rootPath, Uri uri) {
        DocumentFile file = DocumentFile.fromTreeUri(context, uri);
        if (file != null && file.canWrite()) {
            SharedPreferences perf = PreferenceManager.getDefaultSharedPreferences(context);
            perf.edit().putString(rootPath, uri.toString()).apply();
            return true;
        } else {
            Log.e(TAG, "no write permission: " + rootPath);
        }
        return false;
    }

    public static boolean checkWritableRootPath(Context context, String rootPath) {
        File root = new File(rootPath);
        if (!root.canWrite()) {

            if (DocumentsUtils.isOnExtSdCard(root, context)) {
                DocumentFile documentFile = DocumentsUtils.getDocumentFile(root, true, context);
                return documentFile == null || !documentFile.canWrite();
            } else {
                SharedPreferences perf = PreferenceManager.getDefaultSharedPreferences(context);

                String documentUri = perf.getString(rootPath, "");

                if (documentUri == null || documentUri.isEmpty()) {
                    return true;
                } else {
                    DocumentFile file = DocumentFile.fromTreeUri(context, Uri.parse(documentUri));
                    return !(file != null && file.canWrite());
                }
            }
        }
        return false;
    }

    private static void copyFile (
            Context context, File srcFile, File destFile) throws Exception {
        InputStream fis=DocumentsUtils.getInputStream(context,srcFile);
        if (fis==null) fis=new FileInputStream(srcFile);
        OutputStream fos=DocumentsUtils.getOutputStream(context,destFile);
        if (fos==null) fos=new FileOutputStream(destFile);
        int len = fis.available();
        if (len>MAX_BUFFER_SIZE) len=MAX_BUFFER_SIZE;
        byte flush[] =new byte [len];
        while((len=fis.read(flush))>0) {
            fos.write(flush,0,len);
        }
        fos.flush();
        fis.close();
        fos.close();
    }

    private static void copyTree (
            Context context,File srcFolder,File destFolder)
            throws Exception{
        DocumentFile DestFolder=DocumentsUtils.getDocumentFile(destFolder,true,context);
        if (DestFolder==null) destFolder.mkdirs();
        DocumentFile SrcFolder=DocumentsUtils.getDocumentFile(srcFolder,true,context);
        String name;
        File srcSub,destSub;
        if (SrcFolder != null) {
            DocumentFile[] SrcSubs = SrcFolder.listFiles();
            for(DocumentFile SrcSub:SrcSubs) {
                name = SrcSub.getName();
                srcSub=new File(srcFolder.getAbsolutePath(), name);
                destSub=new File(destFolder.getAbsolutePath(), name);
                if (SrcSub.isDirectory()){
                    DocumentsUtils.copyTree(context,srcSub,destSub);
                } else {
                    DocumentsUtils.copyFile(context,srcSub,destSub);
                }
            }
        } else {
            File[] SrcSubs = srcFolder.listFiles();
            for(File SrcSub:SrcSubs) {
                name = SrcSub.getName();
                srcSub=new File(srcFolder.getAbsolutePath(), name);
                destSub=new File(destFolder.getAbsolutePath(), name);
                if (SrcSub.isDirectory()){
                    DocumentsUtils.copyTree(context,srcSub,destSub);
                } else {
                    DocumentsUtils.copyFile(context,srcSub,destSub);
                }
            }
        }
    }

    public static void copy (
            Context context, File src, File dest)
            throws Exception{
        if (src.isDirectory()) copyTree(context,src,dest);
        else copyFile(context,src,dest);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void storageShowOpen(
            String rootPath, Activity context
    ) {
        if (DocumentsUtils.checkWritableRootPath(context, rootPath)) {
            Intent intent = null;
            StorageManager sm = context.getSystemService(StorageManager.class);
            StorageVolume volume = sm.getStorageVolume(new File(rootPath));
            if (volume != null) {
                intent = volume.createAccessIntent(null);
            }
            if (intent == null) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            }
            requestRootPath = rootPath;
            startActivityForResult(context, intent, DocumentsUtils.OPEN_DOCUMENT_TREE_CODE, null);
        }}

        public static boolean storageHandleResult(
                int resultCode, Intent data, Activity context){
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = data.getData();
                DocumentsUtils.saveTreeUri(context, requestRootPath, uri);
                return true;
            }
            return false;
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        public static void storageShowOpenAll (Activity context) {
        for (String rootPath:getExtSdCardPaths(context)){
            storageShowOpen(rootPath,context);
        }
        }
}