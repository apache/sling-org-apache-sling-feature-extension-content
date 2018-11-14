/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.feature.extension.content;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.FeatureConstants;
import org.apache.sling.feature.builder.HandlerContext;
import org.apache.sling.feature.builder.MergeHandler;

import java.util.Map;

public class ContentOrderMergeProcessor implements MergeHandler {

    public static final String DEFAULT_CONTENT_START_ORDER = "default.content.startorder";

    private void processFeature(Feature feature, Extension extension) {
        if (feature == null || extension == null) {
            return;
        }
        String defaultOrder = feature.getVariables().get(DEFAULT_CONTENT_START_ORDER);
        if (defaultOrder != null) {
            for (Artifact a : extension.getArtifacts()) {
                Map<String,String> kvm = a.getMetadata();
                if(kvm.get(Artifact.KEY_START_ORDER) == null) {
                    kvm.put(Artifact.KEY_START_ORDER, defaultOrder);
                }
            }
            feature.getVariables().remove(DEFAULT_CONTENT_START_ORDER);
        }
    }

    @Override
    public boolean canMerge(Extension extension) {
        return extension.getType() == ExtensionType.ARTIFACTS
                && extension.getName().equals(FeatureConstants.EXTENSION_NAME_CONTENT_PACKAGES);
    }

    @Override
    public void merge(HandlerContext context, Feature target, Feature source, Extension targetEx, Extension sourceEx) {

        processFeature(target, targetEx);
        processFeature(source, sourceEx);

        if (targetEx == null) {
            target.getExtensions().add(sourceEx);
            return;
        }
        for (final Artifact a : sourceEx.getArtifacts()) {
            boolean replace = true;
            final Artifact existing = targetEx.getArtifacts().getSame(a.getId());
            if (existing != null && existing.getId().getOSGiVersion().compareTo(a.getId().getOSGiVersion()) > 0) {
                replace = false;
            }

            if (replace) {
                targetEx.getArtifacts().removeSame(a.getId());
                targetEx.getArtifacts().add(a);
            }
        }
    }
}
