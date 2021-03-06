/**
 * Copyright (C) 2012 https://github.com/yelbota/native-dependency-maven-plugin-base
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yelbota.plugins.nd;

import com.yelbota.plugins.nd.utils.UnpackMethod;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class UnpackHelperTest {

    private static final Log NO_LOGGER = null;

    private Map<String, UnpackMethod> mockedUnpackMethods;
    private UnpackMethod mockedUnpackedMethod = mock(UnpackMethod.class);

    private class UnpackHelperWithExtraLogging extends UnpackHelper {
        private boolean logAlreadyUnpackedCalled = false;

        @Override
        protected void logAlreadyUnpacked() {
            this.logAlreadyUnpackedCalled = true;
        }
    }

    @BeforeMethod
    public void setUp() {
        this.mockedUnpackedMethod = mock(UnpackMethod.class);
        Map<String, UnpackMethod> unpackMethodMap = new HashMap<String, UnpackMethod>();
        unpackMethodMap.put("mock-type", this.mockedUnpackedMethod);
        this.mockedUnpackMethods = Collections.unmodifiableMap(unpackMethodMap);
    }

    @Test
    public void testUnpackWhenFileTypeIsUnknown() throws Exception {
        UnpackHelper unpackHelper = new UnpackHelper();
        File invalidDirectory = Files.createTempFile("unpack-help-test", ".mock").toFile();
        try {
            ArtifactStub artifact = createArtifactStub();
            artifact.setType("unknown-type");
            unpackHelper.unpack(invalidDirectory, artifact, this.mockedUnpackMethods, NO_LOGGER);
            fail("should fail unpack call if the file type is invalid");
        } catch (MojoFailureException expected) {
            assertThat(expected.getMessage(), containsString("unknown type"));
        }
        verifyZeroInteractions(this.mockedUnpackedMethod);
    }

    @Test
    public void testUnpackWhenDirectoryIsInvalid() throws Exception {
        UnpackHelper unpackHelper = new UnpackHelper();
        File invalidDirectory = Files.createTempFile("unpack-help-test", ".mock").toFile();
        try {
            ArtifactStub artifact = createArtifactStub();
            unpackHelper.unpack(invalidDirectory, artifact, this.mockedUnpackMethods, NO_LOGGER);
            fail("should fail unpack call if the directory is invalid");
        } catch (MojoFailureException expected) {
            assertThat(expected.getMessage(), containsString("must be directory"));
        }
        verifyZeroInteractions(this.mockedUnpackedMethod);
    }

    @Test
    public void testUnpackWhenDirectoryToUnpackCanNotBeCreated() throws Exception {
        UnpackHelper unpackHelper = new UnpackHelper();
        Set<PosixFilePermission> readOnlyPermissions = PosixFilePermissions.fromString("r-xr-xr-x");
        FileAttribute<Set<PosixFilePermission>> fileAttributes = PosixFilePermissions.asFileAttribute(readOnlyPermissions);

        File readOnlyRootDirectory = Files.createTempDirectory("unpack-helper-test", fileAttributes).toFile();
        File invalidDirectory = new File(readOnlyRootDirectory, "should-fail-to-create");
        try {
            ArtifactStub artifact = createArtifactStub();
            unpackHelper.unpack(invalidDirectory, artifact, this.mockedUnpackMethods, NO_LOGGER);
            fail("should fail unpack call if the directory is invalid");
        } catch (MojoFailureException expected) {
            assertThat(expected.getMessage(), containsString("could not create directory"));
        }
        verifyZeroInteractions(this.mockedUnpackedMethod);
    }

    @Test
    public void testUnpackWhenUnpackMethodThrowsException() throws Exception {
        UnpackMethod mockedUnpackMethod = mock(UnpackMethod.class);
        doThrow(new UnpackMethod.UnpackMethodException("something went wrong")).when(mockedUnpackMethod).unpack(any(File.class), any(File.class), any(Log.class));
        Map<String, UnpackMethod> unpackMethods = new HashMap<String, UnpackMethod>();
        unpackMethods.put("mock-type", mockedUnpackMethod);

        File directory = new File(System.getProperty("java.io.tmpdir"), "unpack-helper-test-" + UUID.randomUUID());
        ArtifactStub artifact = createArtifactStub();
        UnpackHelperWithExtraLogging unpackHelper = new UnpackHelperWithExtraLogging();
        try {
            unpackHelper.unpack(directory, artifact, unpackMethods, NO_LOGGER);
            fail("should fail unpack call if UnpackMethod fails");
        } catch (UnpackHelper.UnpackHelperException expected) { /* no op */ }

        assertFalse(unpackHelper.logAlreadyUnpackedCalled, "the first time unpack is invoked it does not see the artifact as unpacked");
        assertFalse(Files.exists(Paths.get(directory.getAbsolutePath(), UnpackHelper.UNPACKED_COMPLETED_FLAG_FILE)), "flag file is not created");
        verify(mockedUnpackMethod).unpack(any(File.class), any(File.class), any(Log.class));
    }

    @Test
    public void testUnpackWhenUnpackDirectoryDoesNotExist() throws Exception {
        File directory = new File(System.getProperty("java.io.tmpdir"), "unpack-helper-test-" + UUID.randomUUID());
        ArtifactStub artifact = createArtifactStub();
        UnpackHelperWithExtraLogging unpackHelper = new UnpackHelperWithExtraLogging();

        unpackHelper.unpack(directory, artifact, this.mockedUnpackMethods, NO_LOGGER);
        assertFalse(unpackHelper.logAlreadyUnpackedCalled, "the first time unpack is invoked it does not see the artifact as unpacked");
        assertTrue(Files.exists(Paths.get(directory.getAbsolutePath(), UnpackHelper.UNPACKED_COMPLETED_FLAG_FILE)), "flag file is created");
        verify(this.mockedUnpackedMethod).unpack(any(File.class), any(File.class), any(Log.class));
    }

    @Test
    public void testTwoConsecutiveCallsToUnpackWhenDirectoryAlreadyExists() throws Exception {
        File directory = Files.createTempDirectory("unpack-helper-test").toFile();
        ArtifactStub artifact = createArtifactStub();
        UnpackHelperWithExtraLogging unpackHelper = new UnpackHelperWithExtraLogging();

        unpackHelper.unpack(directory, artifact, this.mockedUnpackMethods, NO_LOGGER);
        assertFalse(unpackHelper.logAlreadyUnpackedCalled, "the first time unpack is invoked it does not see the artifact as unpacked");
        assertTrue(Files.exists(Paths.get(directory.getAbsolutePath(), UnpackHelper.UNPACKED_COMPLETED_FLAG_FILE)), "flag file is created");
        verify(this.mockedUnpackedMethod).unpack(any(File.class), any(File.class), any(Log.class));

        unpackHelper.unpack(directory, artifact, this.mockedUnpackMethods, NO_LOGGER);
        assertTrue(unpackHelper.logAlreadyUnpackedCalled, "the second time unpack is invoked it sees the artifact as already unpacked");
        assertTrue(Files.exists(Paths.get(directory.getAbsolutePath(), UnpackHelper.UNPACKED_COMPLETED_FLAG_FILE)),  "flag file is not removed");
        verifyNoMoreInteractions(this.mockedUnpackedMethod);
    }

    private ArtifactStub createArtifactStub() throws IOException {
        ArtifactStub artifact = new ArtifactStub();
        artifact.setType("mock-type");
        artifact.setFile(Files.createTempFile("unpack-help-test-artifact", ".mock").toFile());
        return artifact;
    }
}
