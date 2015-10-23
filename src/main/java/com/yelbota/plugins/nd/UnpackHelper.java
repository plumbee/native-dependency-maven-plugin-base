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
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.sonatype.aether.spi.connector.ArtifactDownload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * @author Aleksey Fomkin
 */
public class UnpackHelper {

    public static final String UNPACKED_COMPLETED_FLAG_FILE = "unpack-completed.flag";

    /**
     * @author Aleksey Fomkin
     */
    class UnpackHelperException extends MojoFailureException {

        public UnpackHelperException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    //-------------------------------------------------------------------------
    //
    //  Public methods
    //
    //-------------------------------------------------------------------------

    public void unpack(File directory, Artifact artifact,
                       Map<String, UnpackMethod> unpackMethods) throws MojoFailureException {
        unpack(directory, artifact, unpackMethods, null);
    }

    public void unpack(File directory, Artifact artifact,
                       Map<String, UnpackMethod> unpackMethods,
                       Log log) throws MojoFailureException {
        unpack(directory, artifact.getFile(), getUnpackMethod(artifact.getType(), unpackMethods, log), log);
    }

    public void unpack(File directory, ArtifactDownload artifactDownload,
                       Map<String, UnpackMethod> unpackMethods) throws MojoFailureException {

        unpack(directory, artifactDownload, unpackMethods, null);
    }

    public void unpack(File directory, ArtifactDownload artifactDownload,
                       Map<String, UnpackMethod> unpackMethods,
                       Log log) throws MojoFailureException {
        org.sonatype.aether.artifact.Artifact artifact = artifactDownload.getArtifact();
        if (artifact != null) {
            unpack(directory, artifactDownload.getFile(), getUnpackMethod(artifact.getExtension(), unpackMethods, log), log);
        } else {
            throw new MojoFailureException(artifactDownload + " has no valid artifact reference.");
        }
    }

    private UnpackMethod getUnpackMethod(String type, Map<String, UnpackMethod> unpackMethodMap, Log log) throws MojoFailureException {
        if (log != null) log.info("getting method for artifact type " + type);
        UnpackMethod unpackMethod = unpackMethodMap.get(type);
        if (unpackMethod == null) {
            throw new MojoFailureException(String.format("unknown type: %s", type));
        }
        return unpackMethod;
    }

    /**
     * Unpack `artifact` to `directory`.
     * @throws UnpackHelperException
     */
    private void unpack(File directory, File artifactFile,
                        UnpackMethod unpackMethod,
                        Log log) throws MojoFailureException {

        if (!directory.exists()) {
            if (log != null) log.info("dir '" + directory + "' does not exist");
            if (!directory.mkdirs()) {
                throw new MojoFailureException(String.format("could not create directory: %s", directory.getAbsolutePath()));
            }
        }

        if (!directory.isDirectory()) {
            throw new MojoFailureException(directory.getAbsolutePath() + ", which must be directory for unpacking, now is file");
        }

        if (Files.exists(Paths.get(directory.getAbsolutePath(), UNPACKED_COMPLETED_FLAG_FILE))) {
            if (log != null) log.info("already unpacked?");
            logAlreadyUnpacked();
        } else {
            tryUnpacking(directory, artifactFile, unpackMethod, log);
        }
    }

    private void tryUnpacking(File directory, File artifactFile, UnpackMethod unpackMethod, Log log) throws UnpackHelperException {
        try {
            logUnpacking();
            if (log != null) log.info("artifact file: " + artifactFile);
            unpackMethod.unpack(artifactFile, directory, log);
            Files.createFile(Paths.get(directory.getAbsolutePath(), UNPACKED_COMPLETED_FLAG_FILE));
        } catch (IOException e) {
            throw new UnpackHelperException("Can't unpack " + artifactFile, e);
        } catch (UnpackMethod.UnpackMethodException e) {
            throw new UnpackHelperException("Can't unpack " + artifactFile, e);
        }
    }

    //-------------------------------------------------------------------------
    //
    //  Abstract methods
    //
    //-------------------------------------------------------------------------

    protected void logAlreadyUnpacked() {
        // Empty default implementation.
    }

    protected void logUnpacking() {
        // Empty default implementation.
    }
}