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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Artifacts;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.HandlerContext;
import org.junit.Test;
import org.mockito.Mock;

public class ContentOrderMergeProcessorTest {

    @Mock
    HandlerContext handlerContext;

    @Test
    public void testMergeDifferentStartOrders() {
        final Artifacts targetArtifacts = new Artifacts();

        final ArtifactId tid1 = ArtifactId.fromMvnId("sling:targetpack1:1");
        Artifact targetpack1 = new Artifact(tid1);
        assertNull(targetpack1.getMetadata().get("start-order"));
        targetArtifacts.add(targetpack1);
        
        final ArtifactId tid2 = ArtifactId.fromMvnId("sling:targetpack2:1");
        Artifact targetpack2 = new Artifact(tid2);
        assertNull(targetpack2.getMetadata().get("start-order"));
        targetArtifacts.add(targetpack2);
        
        final Artifacts sourceArtifacts = new Artifacts();
        
        final ArtifactId sid1 = ArtifactId.fromMvnId("sling:sourcepack1:1");
        final Artifact sourcepack1 = new Artifact(sid1);
        assertNull(sourcepack1.getMetadata().get("start-order"));
        sourceArtifacts.add(sourcepack1);
        
        final ArtifactId sid2 = ArtifactId.fromMvnId("sling:sourcepack2:1");
        Artifact sourcepack2 = new Artifact(sid2);
        assertNull(sourcepack2.getMetadata().get("start-order"));
        sourceArtifacts.add(sourcepack2);

        final Extension targetEx = new Extension(ExtensionType.ARTIFACTS, "content-package", false);
        targetEx.getArtifacts().addAll(targetArtifacts);
        final Feature target = new Feature(ArtifactId.fromMvnId("sling:targettest:1"));
        target.getExtensions().add(targetEx);
        target.getVariables().put(ContentOrderMergeProcessor.DEFAULT_CONTENT_START_ORDER, "1");
        
        final Extension sourceEx = new Extension(ExtensionType.ARTIFACTS, "content-package", false);
        sourceEx.getArtifacts().addAll(sourceArtifacts);
        final Feature source = new Feature(ArtifactId.fromMvnId("sling:sourcetest:1"));
        source.getExtensions().add(sourceEx);
        source.getVariables().put(ContentOrderMergeProcessor.DEFAULT_CONTENT_START_ORDER, "2");
        
        final Set<Artifact> testArtifacts = new HashSet<>(Arrays.asList(targetpack1, targetpack2, sourcepack1, sourcepack2));

        ContentOrderMergeProcessor comp = new ContentOrderMergeProcessor();
        comp.merge(handlerContext, target, source, targetEx, sourceEx);
      

        Artifacts mergedArtifacts = targetEx.getArtifacts();
        assertTrue(mergedArtifacts.containsAll(testArtifacts));

        assertEquals("1", mergedArtifacts.getSame(tid1).getMetadata().get("start-order"));
        assertEquals("1", mergedArtifacts.getSame(tid2).getMetadata().get("start-order"));
        assertEquals("2", mergedArtifacts.getSame(sid1).getMetadata().get("start-order"));
        assertEquals("2", mergedArtifacts.getSame(sid2).getMetadata().get("start-order"));
        
    }

    
    @Test
    public void testEmptyTargetExtension() {
        final Artifacts sourceArtifacts = new Artifacts();
        
        final ArtifactId sid1 = ArtifactId.fromMvnId("sling:sourcepack1:1");
        final Artifact sourcepack1 = new Artifact(sid1);
        assertNull(sourcepack1.getMetadata().get("start-order"));
        sourceArtifacts.add(sourcepack1);

        final Feature target = new Feature(ArtifactId.fromMvnId("sling:targettest:1"));

        final Extension sourceEx = new Extension(ExtensionType.ARTIFACTS, "content-package", false);
        sourceEx.getArtifacts().addAll(sourceArtifacts);
        
        final Feature source = new Feature(ArtifactId.fromMvnId("sling:sourcetest:1"));
        source.getExtensions().add(sourceEx);
        source.getVariables().put(ContentOrderMergeProcessor.DEFAULT_CONTENT_START_ORDER, "2");

        ContentOrderMergeProcessor comp = new ContentOrderMergeProcessor();
        comp.merge(handlerContext, target, source, null, sourceEx);
      

        Artifacts mergedArtifacts = target.getExtensions().getByName("content-package").getArtifacts();
        assertTrue(mergedArtifacts.contains(sourcepack1));
        assertEquals("2", mergedArtifacts.getSame(sid1).getMetadata().get("start-order"));
    }

}
