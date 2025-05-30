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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.util.Dictionary;
import java.util.Iterator;

import org.apache.jackrabbit.vault.packaging.registry.impl.AbstractPackageRegistry.SecurityConfig;
import org.apache.jackrabbit.vault.packaging.registry.impl.FSPackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.impl.InstallationScope;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionState;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.launcher.spi.extensions.ExtensionContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class ContentHandlerTest {


    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Mock
    ExtensionContext extensionContext;

    /**
     * Test package A-1.0. Depends on B and C-1.X
     */
    private static final String COORDINATES_TEST_PACKAGE_A_10 = "my_packages:test_a:1.0";
    private static String TEST_PACKAGE_A_10 = "testpackages/test_a-1.0.zip";
    private static ArtifactId TEST_PACKAGE_AID_A_10 = ArtifactId.fromMvnId(COORDINATES_TEST_PACKAGE_A_10);

    /**
     * Test package B-1.0. Depends on C
     */
    private static final String COORDINATES_TEST_PACKAGE_B_10 = "my_packages:test_b:1.0";
    private static String TEST_PACKAGE_B_10 = "testpackages/test_b-1.0.zip";
    private static ArtifactId TEST_PACKAGE_AID_B_10 = ArtifactId.fromMvnId(COORDINATES_TEST_PACKAGE_B_10);

    /**
     * Test package C-1.0
     */
    private static final String COORDINATES_TEST_PACKAGE_C_10 = "my_packages:test_c:1.0";
    private static String TEST_PACKAGE_C_10 = "testpackages/test_c-1.0.zip";
    private static ArtifactId TEST_PACKAGE_AID_C_10 = ArtifactId.fromMvnId(COORDINATES_TEST_PACKAGE_C_10);
    private static ArtifactId TEST_PACKAGE_AID_C_10_SNAPSHOT = TEST_PACKAGE_AID_C_10.changeVersion("1.0-SNAPSHOT");

    @Before
    public void setUp() throws Exception {
        URL testA = this.getClass().getResource(TEST_PACKAGE_A_10);
        when(extensionContext.getArtifactFile(TEST_PACKAGE_AID_A_10)).thenReturn(testA);
        URL testB = this.getClass().getResource(TEST_PACKAGE_B_10);
        when(extensionContext.getArtifactFile(TEST_PACKAGE_AID_B_10)).thenReturn(testB);
        URL testC = this.getClass().getResource(TEST_PACKAGE_C_10);
        when(extensionContext.getArtifactFile(TEST_PACKAGE_AID_C_10)).thenReturn(testC);
        when(extensionContext.getArtifactFile(TEST_PACKAGE_AID_C_10_SNAPSHOT)).thenReturn(testC);
        
        when(extensionContext.getLogger()).thenReturn(LoggerFactory.getLogger(getClass().getName()));
        
        System.setProperty(ContentHandler.PACKAGEREGISTRY_HOME, testFolder.getRoot().toString());
        
        System.clearProperty(ContentHandler.class.getPackageName()+".reinstallSnapshots");
    }

    /**
     * Validates that the content handler creates the execution plan correctly
     * 
     * @throws Exception
     */
    @Test
    public void testMultipleStartOrders() throws Exception {
        ContentHandler ch = new ContentHandler();
        
        Extension ext = new Extension(ExtensionType.ARTIFACTS, "content-packages", ExtensionState.OPTIONAL);
        
        Artifact artifactA = new Artifact(TEST_PACKAGE_AID_A_10);
        Artifact artifactB = new Artifact(TEST_PACKAGE_AID_B_10);
        Artifact artifactC = new Artifact(TEST_PACKAGE_AID_C_10);
        artifactA.getMetadata().put("start-order", "2");
        artifactB.getMetadata().put("start-order", "1");
        artifactC.getMetadata().put("start-order", "1");
        ext.getArtifacts().add(artifactA);
        ext.getArtifacts().add(artifactB);
        ext.getArtifacts().add(artifactC);
        
        final String[] executionplans = execute(ch, ext, false);

        final String expected_0 =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<executionPlan version=\"1.0\">\n" +
                        "    <task cmd=\"extract\" packageId=\"my_packages:test_c:1.0\">\n" +
                        "        <options type=\"ImportOptions\">\n" +
                        "            <isStrict>false</isStrict>\n" +
                        "            <autoSaveThreshold>-1</autoSaveThreshold>\n" +
                        "            <nonRecursive>false</nonRecursive>\n" +
                        "            <dryRun>false</dryRun>\n" +
                        "        </options>\n" +
                        "    </task>\n" +
                        "    <task cmd=\"extract\" packageId=\"my_packages:test_b:1.0\">\n" +
                        "        <options type=\"ImportOptions\">\n" +
                        "            <isStrict>false</isStrict>\n" +
                        "            <autoSaveThreshold>-1</autoSaveThreshold>\n" +
                        "            <nonRecursive>false</nonRecursive>\n" +
                        "            <dryRun>false</dryRun>\n" +
                        "        </options>\n" +
                        "    </task>\n" +
                        "</executionPlan>\n";

        assertEquals(expected_0, executionplans[0]);
        final String expected_1 =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<executionPlan version=\"1.0\">\n" +
                        "    <task cmd=\"extract\" packageId=\"my_packages:test_a:1.0\">\n" +
                        "        <options type=\"ImportOptions\">\n" +
                        "            <isStrict>false</isStrict>\n" +
                        "            <autoSaveThreshold>-1</autoSaveThreshold>\n" +
                        "            <nonRecursive>false</nonRecursive>\n" +
                        "            <dryRun>false</dryRun>\n" +
                        "        </options>\n" +
                        "    </task>\n" +
                        "</executionPlan>\n";

        assertEquals(expected_1, executionplans[1]);
        assertEquals(2, executionplans.length);
    }
    
    /**
     * Validates that a package that is already registered is skipped from the execution plan.
     * 
     * @throws Exception
     */
    @Test
    public void duplicatePackage() throws Exception {
        
        FSPackageRegistry registry = new FSPackageRegistry(testFolder.getRoot(), InstallationScope.UNSCOPED, new SecurityConfig(null, null), true);
        registry.register(getClass().getResourceAsStream(TEST_PACKAGE_C_10), false);
        
        ContentHandler ch = new ContentHandler();
        Extension ext = new Extension(ExtensionType.ARTIFACTS, "content-packages", ExtensionState.OPTIONAL);
        Artifact artifactC = new Artifact(TEST_PACKAGE_AID_C_10);
        artifactC.getMetadata().put("start-order", "1");
        ext.getArtifacts().add(artifactC);
        final String[] executionplans = execute(ch, ext, false);

        final String expected_0 =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<executionPlan version=\"1.0\"/>\n";

        assertEquals(expected_0, executionplans[0]);
        assertEquals(1, executionplans.length);
        
    }
    
    /**
     * Validates that a SNAPSHOT package that is already registered is included in the execution plan when the corresponding system property is set.
     * 
     * @throws Exception
     */
    @Test
    public void duplicateSnapshotPackage_reinstall() throws Exception {
        
        System.setProperty(ContentHandler.class.getPackageName()+".reinstallSnapshots", "true");
        
        FSPackageRegistry registry = new FSPackageRegistry(testFolder.getRoot(), InstallationScope.UNSCOPED, new SecurityConfig(null, null), true);
        registry.register(getClass().getResourceAsStream(TEST_PACKAGE_C_10), false);
        
        ContentHandler ch = new ContentHandler();
        Extension ext = new Extension(ExtensionType.ARTIFACTS, "content-packages", ExtensionState.OPTIONAL);
        Artifact artifactC = new Artifact(TEST_PACKAGE_AID_C_10_SNAPSHOT);
        artifactC.getMetadata().put("start-order", "1");
        ext.getArtifacts().add(artifactC);
        
        final String[] executionplans = execute(ch, ext, true);

        final String expected_0 =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<executionPlan version=\"1.0\">\n" +
                        "    <task cmd=\"extract\" packageId=\"my_packages:test_c:1.0\">\n" +
                        "        <options type=\"ImportOptions\">\n" +
                        "            <isStrict>false</isStrict>\n" +
                        "            <autoSaveThreshold>-1</autoSaveThreshold>\n" +
                        "            <nonRecursive>false</nonRecursive>\n" +
                        "            <dryRun>false</dryRun>\n" +
                        "        </options>\n" +
                        "    </task>\n" +
                        "</executionPlan>\n";

        assertEquals(expected_0, executionplans[0]);
        assertEquals(1, executionplans.length);
    }

    /**
     * Validates that a SNAPSHOT package that is already registered is included in the execution plan when the corresponding system property is not set.
     * 
     * @throws Exception
     */
    @Test
    public void duplicateSnapshotPackage_skip() throws Exception {
        
        FSPackageRegistry registry = new FSPackageRegistry(testFolder.getRoot(), InstallationScope.UNSCOPED, new SecurityConfig(null, null), true);
        registry.register(getClass().getResourceAsStream(TEST_PACKAGE_C_10), false);
        
        ContentHandler ch = new ContentHandler();
        Extension ext = new Extension(ExtensionType.ARTIFACTS, "content-packages", ExtensionState.OPTIONAL);
        Artifact artifactC = new Artifact(TEST_PACKAGE_AID_C_10_SNAPSHOT);
        artifactC.getMetadata().put("start-order", "1");
        ext.getArtifacts().add(artifactC);
        
        final String[] executionplans = execute(ch, ext, false);

        final String expected_0 =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<executionPlan version=\"1.0\"/>\n";

        assertEquals(expected_0, executionplans[0]);
        assertEquals(1, executionplans.length);
    }

    private String[] execute(ContentHandler ch, Extension ext, boolean expectedReinstallSnapshots) throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Dictionary<String, Object>> executionPlanCaptor = ArgumentCaptor.forClass(Dictionary.class);

        ch.handle(extensionContext, ext);
        verify(extensionContext).addConfiguration(eq("org.apache.sling.jcr.packageinit.impl.ExecutionPlanRepoInitializer"), any(), executionPlanCaptor.capture());
        verify(extensionContext).addConfiguration(eq("org.UNSHADE.apache.jackrabbit.vault.packaging.registry.impl.FSPackageRegistry"), any(), any());
        Iterator<Dictionary<String, Object>> dictIt = executionPlanCaptor.getAllValues().iterator();
        Dictionary<String, Object> dict = dictIt.next();
        final String[] executionplans = (String[]) dict.get("executionplans");
        final String statusFileHome = (String)dict.get("statusfilepath");
        final Boolean reinstallSnapshots = (Boolean)dict.get("reinstallSnapshots");
        assertEquals("reinstallSnapshots configuration property", expectedReinstallSnapshots, reinstallSnapshots);
        File executedPlansFile = new File(testFolder.getRoot(), "executedplans.file");
        assertEquals(executedPlansFile.getAbsolutePath(), statusFileHome);
        return executionplans;
    }
}
