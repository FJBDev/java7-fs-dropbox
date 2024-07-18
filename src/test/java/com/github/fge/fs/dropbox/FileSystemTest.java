package com.github.fge.fs.dropbox;

import com.github.fge.filesystem.provider.FileSystemRepository;
import com.github.fge.fs.dropbox.misc.DropBoxIOException;
import com.github.fge.fs.dropbox.provider.DropBoxFileSystemProvider;
import com.github.fge.fs.dropbox.provider.DropBoxFileSystemRepository;
import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FileSystemTest {
    private static final String ACCESS_TOKEN = System.getenv("DROPBOX_ACCESSTOKEN");
    private static FileSystemProvider provider;
    private static URI uri;
    private static Map<String, String> env;
    private static String testDirectoryPath;

    @BeforeClass
    public static void setUpClass() throws Exception {
        uri = URI.create("dropbox://foo/");
        env = new HashMap<>();
        env.put("accessToken", ACCESS_TOKEN);

        final FileSystemRepository repository = new DropBoxFileSystemRepository();
        provider = new DropBoxFileSystemProvider(repository);

        testDirectoryPath = "/test-" + UUID.randomUUID().toString();

        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Files.createDirectory(dropboxfs.getPath(testDirectoryPath));
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
    }

    @AfterClass
    public static void tearDown() throws Exception {
        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            deleteRecursive(dropboxfs, dropboxfs.getPath(testDirectoryPath));
        }
    }

    private static void deleteRecursive(FileSystem fs, Path directoryPath) throws IOException {
        if (Files.exists(directoryPath)) {
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(directoryPath)) {
                for (Path path : paths) {
                    if (Files.isDirectory(path)) {
                        deleteRecursive(fs, path);
                    } else {
                        Files.delete(fs.getPath(path.toString()));
                    }
                }
            }
            Files.delete(fs.getPath(directoryPath.toString()));
        }
    }

    @Test
    public void testDirectoryStreamRoot() throws IOException {
        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Files.newDirectoryStream(dropboxfs.getPath(""));
        }
    }

    @Test
    public void testDirectoryStream() throws IOException {
        try (final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Files.newDirectoryStream(dropboxfs.getPath(testDirectoryPath));
        }
    }

    @Test
    public void testCheckAccessFile() throws IOException {
        try (final FileSystem memfs = MemoryFileSystemBuilder.newEmpty().build("test");
                final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Path source = memfs.getPath("/file.txt");
            Files.createFile(source);
            Path fileExists = dropboxfs.getPath(testDirectoryPath + "/checkAccess_file_exists.txt");
            Files.copy(source, fileExists);

            boolean thrown = false;

            dropboxfs.provider().checkAccess(fileExists);
            dropboxfs.provider().checkAccess(fileExists, AccessMode.READ);
            dropboxfs.provider().checkAccess(fileExists, AccessMode.WRITE);
            try {
                dropboxfs.provider().checkAccess(fileExists, AccessMode.EXECUTE);
            } catch (AccessDeniedException e) {
                thrown = true;
            }
            Assert.assertTrue(thrown);
        }
    }

    @Test
    public void testCheckAccessNoSuchFile() throws IOException {
        try (final FileSystem memfs = MemoryFileSystemBuilder.newEmpty().build("test");
                final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Path noSuchFile = dropboxfs.getPath(testDirectoryPath + "/checkAccess_file_not_found.txt");

            boolean thrown = false;

            try {
                dropboxfs.provider().checkAccess(noSuchFile);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertTrue(thrown);
            thrown = false;
            try {
                dropboxfs.provider().checkAccess(noSuchFile, AccessMode.READ);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertTrue(thrown);
            thrown = false;
            try {
                dropboxfs.provider().checkAccess(noSuchFile, AccessMode.WRITE);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertTrue(thrown);
            thrown = false;
            try {
                dropboxfs.provider().checkAccess(noSuchFile, AccessMode.EXECUTE);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertTrue(thrown);
        }
    }

    @Test
    public void testCheckAccessDirectory() throws IOException {
        try (final FileSystem memfs = MemoryFileSystemBuilder.newEmpty().build("test");
                final FileSystem dropboxfs = provider.newFileSystem(uri, env)) {
            Path directory = Files.createDirectory(dropboxfs.getPath(testDirectoryPath + "/checkAccess_directory"));

            boolean thrown = false;

            try {
                dropboxfs.provider().checkAccess(directory);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertFalse(thrown);
            thrown = false;
            try {
                dropboxfs.provider().checkAccess(directory, AccessMode.READ);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertFalse(thrown);
            thrown = false;
            try {
                dropboxfs.provider().checkAccess(directory, AccessMode.WRITE);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertFalse(thrown);
            thrown = false;
            try {
                dropboxfs.provider().checkAccess(directory, AccessMode.EXECUTE);
            } catch (NoSuchFileException e) {
                thrown = true;
            }
            Assert.assertFalse(thrown);
        }
    }

}
