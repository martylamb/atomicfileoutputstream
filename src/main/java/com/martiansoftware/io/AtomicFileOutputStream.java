package com.martiansoftware.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * This class is similar to the standard java.io.FileOutputStream, except that
 * the destination file is not touched until the stream is closed.  The destination
 * file is then atomically updated (replaced if necessary) with the freshly-written contents.
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
    
    private final Path _dest;
    private final Path _tmp;
    private final Path _bak;
    private final FileLock _fileLock;
    private final FileOutputStream _out;
    
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
        _fileLock = new RandomAccessFile(_dest.toFile(), "rw").getChannel().tryLock();
        try {
            _tmp = Files.createTempFile(_dest.getParent(), 
                                        _dest.getName(_dest.getNameCount() - 1).toString() + ".", 
                                        ".tmp");
            _bak = _tmp.resolveSibling(_tmp.getFileName().toString().replaceAll("\\.tmp$", ".bak"));            
            _out = new FileOutputStream(_tmp.toFile());
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
    
    @Override public void close() throws IOException {
        io(() -> _out.close());
        io(() -> finish());
    }    
    
    @Override public void flush() throws IOException {
        io(() -> _out.flush());
    }
    
    @Override public void write(byte[] b) throws IOException {
        io(() -> _out.write(b));
    }
    
    @Override public void write(byte[] b, int off, int len) throws IOException {
        io(() -> _out.write(b, off, len));
    }
    
    @Override public void write(int b) throws IOException {
        io(() -> _out.write(b));
    }
    
    // wraps around an IO operation to clean up any temporary files if an exception occurs
    private void io(DelegatedIo d) throws IOException {
        check();
        try { d.io(); }
        catch (IOException e) {
            cleanup();
            throw e;            
        }        
    }

    // perform the final operations to move the temporary file to its final destination
    private void finish() throws IOException {
        check();
        try {
            // copy original to .bak
            Files.copy(_dest, _bak, StandardCopyOption.REPLACE_EXISTING);
            // atomically move tmp over original
            Files.move(_tmp, _dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            cleanup();
            throw(e);
        }
        cleanup();
        Files.deleteIfExists(_bak); // only delete backup if everything works
    }
    
    private void check() throws IOException {
        if (!_fileLock.isValid()) throw new IOException("Stream is closed or invalid.");
    }
    
    private void cleanup() {
        synchronized(_fileLock) {
            if (_fileLock.isValid()) {
                try { _fileLock.release(); } catch (IOException ignored) {}
                try { Files.deleteIfExists(_tmp); } catch (IOException ignored) {}
            }
        }
    }
    
    private interface DelegatedIo {
        public void io() throws IOException;
    }
}
