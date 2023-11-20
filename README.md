**Important Notice**: [Repository History Change on 2023-11-12](./NOTICES.md#repository-history-change---2023-11-12)

atomicfileoutputstream
======================

Marty Lamb marty@martiansoftware.com, [Martian Software, Inc.](http://martiansoftware.com)

This is a single-class micro-library that attempts to make (potentially) large file writes either succeed or fail entirely, rather than partway through.

`com.martiansoftware.io.AtomicFileOutputStream` is a drop-in replacement for `java.io.FileOutputStream` that directs all writes to a temporary file, which then atomically replaces the destination file when the stream is closed.

The strategy is to:

1.  Write to a temporary file in the same directory as the destination file.
2.  Create a backup of the original file (if it exists) in the same directory.
3.  Atomically copy the temporary file to the destination file.
4.  Delete the backup file.

If anything goes wrong, the temporary file will be deleted.  If anything goes wrong between backing up and replacing the original, a backup file may remain on disk (and can be obtained via getBackup()).  If all goes well, no temporary or backup files will remain on disk after closing the stream.