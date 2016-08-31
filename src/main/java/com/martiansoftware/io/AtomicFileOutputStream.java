package com.martiansoftware.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * This class is similar to the standard java.io.FileOutputStream, except that
 * all writes are directed to a temporary file, which then atomically replaces
 * the destination file when the stream is closed.
 * 
 * The strategy is to:
 * <ol>
 *   <li>write to a temporary file in the same directory as the destination file.</li>
 *   <li>create a backup of the original file (if it exists) in the same directory</li>
 *   <li>atomically copy the temporary file to the destination file</li>
 *   <li>delete the backup file</li>
 * </ol>
 * 
 * If anything goes wrong, the temporary file will be deleted.  If anything goes
 * wrong between backing up and replacing the original, a backup file may
 * remain on disk (and can be obtained via getBackup()).  If all goes well,
 * no temporary or backup files will remain on disk after closing the stream.
 * 
 * @author Marty Lamb
 */
public class AtomicFileOutputStream extends OutputStream {
    
    private final Path _dest; // user-specified destination file
    private final Path _tmp;  // temporary file to which writes are redirected
    private final Path _bak;  // backup of destination file made when stream is closed
    private final FileOutputStream _tmpOut; // writes to tmp file
    
    /**
     * Creates a new AtomicFileOutputStream to write to or replace the specified File
     * @param file the File to write to or replace
     * @throws IOException 
     */
    public AtomicFileOutputStream(File file) throws IOException {
        this (file.toPath());
    }
    
    /**
     * Creates a new AtomicFileOutputStream to write to or replace the File at the specified Path
     * @param path the path of the File to write to or replace
     * @throws IOException 
     */
    public AtomicFileOutputStream(Path path) throws IOException {
        _dest = path;
        try {
            _tmp = Files.createTempFile(_dest.getParent(), 
                                        _dest.getFileName() + ".",
                                        ".tmp");
            _bak = _tmp.resolveSibling(_tmp.getFileName().toString().replaceAll("\\.tmp$", ".bak"));            
            _tmpOut = new FileOutputStream(_tmp.toFile());
        } catch (IOException e) {
            cleanup();
            throw e;
        }
    }

    /**
     * Returns the Path of a possibly nonexistent backup copy of the original File that is being replaced (if any)
     * @return the Path of a possibly nonexistent backup copy of the original File that is being replaced (if any)
     */
    public Path getBackup() { return _bak; }
    
    /**
     * Aborts the write process, leaving the destination file untouched
     * @throws IOException 
     */
    public void cancel() throws IOException {
        try { _tmpOut.close(); } catch (IOException ignored) {}
        io(() -> finish(false));
    }
    
    // wraps around an IO operation to clean up any temporary files if an exception occurs
    private void io(DelegatedIo d) throws IOException {
        try { d.io(); }
        catch (IOException e) {
            cleanup();
            throw e;            
        }        
    }

    // perform the final operations to move the temporary file to its final destination
    private void finish(boolean success) throws IOException {
        if (success) {
            try {
                // copy original to .bak
                if (Files.exists(_dest)) Files.copy(_dest, _bak, StandardCopyOption.REPLACE_EXISTING);
                // atomically move tmp over original
                Files.move(_tmp, _dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                cleanup();
                throw(e);
            }
        }
        cleanup();
        Files.deleteIfExists(_bak); // only delete backup if everything works
    }
    
    private void cleanup() {
        try { Files.deleteIfExists(_tmp); } catch (IOException ignored) {}
    }

    
    @FunctionalInterface private interface DelegatedIo {
        public void io() throws IOException;
    }


    
    @Override public void close() throws IOException {
        io(() -> _tmpOut.close());
        io(() -> finish(true));
    }    

    @Override public void flush() throws IOException {
        io(() -> _tmpOut.flush());
    }
    
    @Override public void write(byte[] b) throws IOException {
        io(() -> _tmpOut.write(b));
    }
    
    @Override public void write(byte[] b, int off, int len) throws IOException {
        io(() -> _tmpOut.write(b, off, len));
    }
    
    @Override public void write(int b) throws IOException {
        io(() -> _tmpOut.write(b));
    }    
    
}
