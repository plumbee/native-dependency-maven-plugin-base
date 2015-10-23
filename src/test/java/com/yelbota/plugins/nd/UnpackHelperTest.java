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
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class UnpackHelperTest {

    private class UnpackHelperWithExtraLogging extends UnpackHelper {
        private boolean logAlreadyUnpackedCalled = false;

        @Override
        protected void logAlreadyUnpacked() {
           this.logAlreadyUnpackedCalled = true;
        }
    }

    @Test
    public void testUnpackWhenFileTypeIsUnknown() throws Exception {
        UnpackMethod mockedUnpackMethod = mock(UnpackMethod.class);

        Map<String, UnpackMethod> unpackMethods = new HashMap<String, UnpackMethod>();
        unpackMethods.put("mock-type", mockedUnpackMethod);

        UnpackHelper unpackHelper = new UnpackHelper();
        File invalidDirectory = Files.createTempFile("unpack-help-test", ".mock").toFile();
        try {
            ArtifactStub artifactStub = new ArtifactStub();
            artifactStub.setType("unknown-type");
            unpackHelper.unpack(invalidDirectory, artifactStub, unpackMethods, null);
            fail("should fail unpack call if the file type is invalid");
        } catch (MojoFailureException expected) {
            assertThat(expected.getMessage(), containsString("unknown type"));
        }
        verifyZeroInteractions(mockedUnpackMethod);
    }

    @Test
    public void testUnpackWhenDirectoryIsInvalid() throws Exception {
        UnpackMethod mockedUnpackMethod = mock(UnpackMethod.class);

        Map<String, UnpackMethod> unpackMethods = new HashMap<String, UnpackMethod>();
        unpackMethods.put("mock-type", mockedUnpackMethod);

        UnpackHelper unpackHelper = new UnpackHelper();
        File invalidDirectory = Files.createTempFile("unpack-help-test", ".mock").toFile();
        try {
            ArtifactStub artifactStub = new ArtifactStub();
            artifactStub.setType("mock-type");
            unpackHelper.unpack(invalidDirectory, artifactStub, unpackMethods, null);
            fail("should fail unpack call if the directory is invalid");
        } catch (MojoFailureException expected) {
            assertThat(expected.getMessage(), containsString("must be directory"));
        }
        verifyZeroInteractions(mockedUnpackMethod);
    }

    @Test
    public void testUnpackWhenCanNotCreateDirectory() throws Exception {
        UnpackMethod mockedUnpackMethod = mock(UnpackMethod.class);

        Map<String, UnpackMethod> unpackMethods = new HashMap<String, UnpackMethod>();
        unpackMethods.put("mock-type", mockedUnpackMethod);

        UnpackHelper unpackHelper = new UnpackHelper();
        Set<PosixFilePermission> readOnlyPermissions = PosixFilePermissions.fromString("r-xr-xr-x");
        FileAttribute<Set<PosixFilePermission>> fileAttributes = PosixFilePermissions.asFileAttribute(readOnlyPermissions);

        File readOnlyRootDirectory = Files.createTempDirectory("unpack-helper-test", fileAttributes).toFile();
        File invalidDirectory = new File(readOnlyRootDirectory, "should-fail-to-create");
        try {
            ArtifactStub artifactStub = new ArtifactStub();
            artifactStub.setType("mock-type");
            unpackHelper.unpack(invalidDirectory, artifactStub, unpackMethods, null);
            fail("should fail unpack call if the directory is invalid");
        } catch (MojoFailureException expected) {
            assertThat(expected.getMessage(), containsString("could not create directory"));
        }
        verifyZeroInteractions(mockedUnpackMethod);
    }

    @Test
    public void testUnpackWhenUnpackMethodThrowsException() throws Exception {
        UnpackMethod mockedUnpackMethod = mock(UnpackMethod.class);
        doThrow(new UnpackMethod.UnpackMethodException("something went wrong")).when(mockedUnpackMethod).unpack(any(File.class), any(File.class), any(Log.class));

        Map<String, UnpackMethod> unpackMethods = new HashMap<String, UnpackMethod>();
        unpackMethods.put("mock-type", mockedUnpackMethod);

        File directory = new File(System.getProperty("java.io.tmpdir"), "unpack-helper-test-" + UUID.randomUUID());

        Artifact artifact = new DefaultArtifact("groupId", "artifactId", "version", "scope", "mock-type", "classifier", new DefaultArtifactHandler());
        artifact.setFile(Files.createTempFile("unpack-help-test-artifact", ".mock").toFile());

        Log mockedLog = mock(Log.class);
        UnpackHelperWithExtraLogging unpackHelper = new UnpackHelperWithExtraLogging();
        try {
            unpackHelper.unpack(directory, artifact, unpackMethods, mockedLog);
            fail("should fail unpack call if UnpackMethod fails");
        } catch (UnpackHelper.UnpackHelperException expected) { /* no op */ }

        assertFalse(unpackHelper.logAlreadyUnpackedCalled, "the first time unpack is invoked it does not see the artifact as unpacked");
        assertFalse(Files.exists(Paths.get(directory.getAbsolutePath(), UnpackHelper.UNPACKED_COMPLETED_FLAG_FILE)), "flag file is not created");
        verify(mockedUnpackMethod).unpack(any(File.class), any(File.class), any(Log.class));
    }

    @Test
    public void testUnpackWhenUnpackDirectoryDoesNotExist() throws Exception {
        UnpackMethod mockedUnpackMethod = mock(UnpackMethod.class);
        Map<String, UnpackMethod> unpackMethods = new HashMap<String, UnpackMethod>();
        unpackMethods.put("mock-type", mockedUnpackMethod);

        File directory = new File(System.getProperty("java.io.tmpdir"), "unpack-helper-test-" + UUID.randomUUID());

        Artifact artifact = new DefaultArtifact("groupId", "artifactId", "version", "scope", "mock-type", "classifier", new DefaultArtifactHandler());
        artifact.setFile(Files.createTempFile("unpack-help-test-artifact", ".mock").toFile());

        Log mockedLog = mock(Log.class);
        UnpackHelperWithExtraLogging unpackHelper = new UnpackHelperWithExtraLogging();
        unpackHelper.unpack(directory, artifact, unpackMethods, mockedLog);
        assertFalse(unpackHelper.logAlreadyUnpackedCalled, "the first time unpack is invoked it does not see the artifact as unpacked");
        assertTrue(Files.exists(Paths.get(directory.getAbsolutePath(), UnpackHelper.UNPACKED_COMPLETED_FLAG_FILE)), "flag file is created");
        verify(mockedUnpackMethod).unpack(any(File.class), any(File.class), any(Log.class));
    }

    @Test
    public void testTwoConsecutiveCallsToUnpackWhenDirectoryAlreadyExists() throws Exception {
        UnpackMethod mockedUnpackMethod = mock(UnpackMethod.class);
        Map<String, UnpackMethod> unpackMethods = new HashMap<String, UnpackMethod>();
        unpackMethods.put("mock-type", mockedUnpackMethod);

        File directory = Files.createTempDirectory("unpack-helper-test").toFile();

        Artifact artifact = new DefaultArtifact("groupId", "artifactId", "version", "scope", "mock-type", "classifier", new DefaultArtifactHandler());
        artifact.setFile(Files.createTempFile("unpack-help-test-artifact", ".mock").toFile());

        Log mockedLog = mock(Log.class);
        UnpackHelperWithExtraLogging unpackHelper = new UnpackHelperWithExtraLogging();
        unpackHelper.unpack(directory, artifact, unpackMethods, mockedLog);
        assertFalse(unpackHelper.logAlreadyUnpackedCalled, "the first time unpack is invoked it does not see the artifact as unpacked");
        assertTrue(Files.exists(Paths.get(directory.getAbsolutePath(), UnpackHelper.UNPACKED_COMPLETED_FLAG_FILE)), "flag file is created");
        verify(mockedUnpackMethod).unpack(any(File.class), any(File.class), any(Log.class));

        unpackHelper.unpack(directory, artifact, unpackMethods, mockedLog);
        assertTrue(unpackHelper.logAlreadyUnpackedCalled, "the second time unpack is invoked it sees the artifact as already unpacked");
        assertTrue(Files.exists(Paths.get(directory.getAbsolutePath(), UnpackHelper.UNPACKED_COMPLETED_FLAG_FILE)),  "flag file is not removed");
        verifyNoMoreInteractions(mockedUnpackMethod);
    }
}