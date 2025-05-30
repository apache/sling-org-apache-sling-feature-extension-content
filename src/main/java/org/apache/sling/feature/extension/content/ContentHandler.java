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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.PackageExistsException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlanBuilder;
import org.apache.jackrabbit.vault.packaging.registry.PackageTask.Type;
import org.apache.jackrabbit.vault.packaging.registry.PackageTaskOptions;
import org.apache.jackrabbit.vault.packaging.registry.impl.AbstractPackageRegistry.SecurityConfig;
import org.apache.jackrabbit.vault.packaging.registry.impl.FSPackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.impl.InstallationScope;
import org.apache.jackrabbit.vault.packaging.registry.taskoption.ImportOptionsPackageTaskOption;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.io.IOUtils;
import org.apache.sling.feature.launcher.spi.LauncherPrepareContext;
import org.apache.sling.feature.launcher.spi.extensions.ExtensionContext;
import org.apache.sling.feature.launcher.spi.extensions.ExtensionHandler;

public class ContentHandler implements ExtensionHandler {
    private static final String SYS_PROP_USE_STRICT_MODE = ContentHandler.class.getPackageName()+ ".useStrictMode";

    private static final String SYS_PROP_REINSTALL_SNAPSHOTS = ContentHandler.class.getPackageName()+ ".reinstallSnapshots";

    public static final String PACKAGEREGISTRY_HOME = "packageregistry.home";

    private static final String REPOSITORY_HOME = "repository.home";

    private static final String REGISTRY_FOLDER = "packageregistry";

    private static ExecutionPlanBuilderWithDetails buildExecutionPlan(Collection<Artifact> artifacts, Set<PackageId> satisfiedPackages, LauncherPrepareContext prepareContext, File registryHome, 
            boolean useStrictMode, boolean reinstallSnapshots) throws Exception {

        List<PackageReference> packageReferences = new ArrayList<>();

        for (final Artifact a : artifacts) {
            final URL file = prepareContext.getArtifactFile(a.getId());
            File tmp = IOUtils.getFileFromURL(file, true, null);
            if (tmp != null && tmp.length() > 0) {
                packageReferences.add(new PackageReference(tmp, a));
            }
        }

        if (!registryHome.exists()) {
            registryHome.mkdirs();
        }

        FSPackageRegistry registry = new FSPackageRegistry(registryHome, InstallationScope.UNSCOPED, new SecurityConfig(null, null), true);

        ExecutionPlanBuilder builder = registry.createExecutionPlan();
        builder.with(satisfiedPackages);
        boolean hasAnySnapshot = false;
        for (PackageReference pkgRef : packageReferences) {
            hasAnySnapshot |= pkgRef.isSnapshot;
            try {
                PackageId pid = registry.registerExternal(pkgRef.file, pkgRef.isSnapshot && reinstallSnapshots);
                ImportOptions importOptions = new ImportOptions();
                importOptions.setStrict(useStrictMode);
                PackageTaskOptions options = new ImportOptionsPackageTaskOption(importOptions);
                builder.addTask().with(pid).withOptions(options).with(Type.EXTRACT);
            } catch (PackageExistsException ex) {
                // Expected - the package is already present
            }
        }
        builder.validate();
        satisfiedPackages.addAll(builder.preview());
        
        return new ExecutionPlanBuilderWithDetails(builder, hasAnySnapshot);
    }

    @Override
    public boolean handle(ExtensionContext context, Extension extension) throws Exception {
        File registryHome = getRegistryHomeDir(context);
        if (extension.getType() == ExtensionType.ARTIFACTS
                && extension.getName().equals(Extension.EXTENSION_NAME_CONTENT_PACKAGES)) {

        	boolean useStrictMode = Boolean.getBoolean(SYS_PROP_USE_STRICT_MODE);
        	boolean reinstallSnapshots = Boolean.getBoolean(SYS_PROP_REINSTALL_SNAPSHOTS);

            Map<Integer, Collection<Artifact>> orderedArtifacts = new TreeMap<>();
            for (final Artifact a : extension.getArtifacts()) {
                int order;
                // content-packages without explicit start-order to be installed last
                if (a.getMetadata().get(Artifact.KEY_START_ORDER) != null) {
                    order = a.getStartOrder();
                } else {
                    order = Integer.MAX_VALUE;
                }
                orderedArtifacts.computeIfAbsent(order, id -> new ArrayList<>()).add(a);
            }
            List<String> executionPlans = new ArrayList<>();
            Set<PackageId> satisfiedPackages = new HashSet<>();
            boolean anyPlanIncludesSnapshots = false;
            for (Collection<Artifact> artifacts : orderedArtifacts.values()) {
                ExecutionPlanBuilderWithDetails builderWithDetails = buildExecutionPlan(artifacts, satisfiedPackages, context, registryHome, useStrictMode, reinstallSnapshots);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                builderWithDetails.builder.save(baos);
                executionPlans.add(baos.toString(StandardCharsets.UTF_8));
                anyPlanIncludesSnapshots |= builderWithDetails.includesSnapshots;
            }
            
            if ( anyPlanIncludesSnapshots ) {
                if ( reinstallSnapshots ) {
                    context.getLogger().info("Found at least one SNAPSHOT package - configuring ExecutionPlanRepoInitializer to reprocess all plans that contain SNAPSHOT packages.");
                } else {
                    context.getLogger().info("Found at least one SNAPSHOT package but ExecutionPlanRepoInitializer is not configured by default to reinstall SNAPSHOTS. Set system property {} to true to enable.", SYS_PROP_REINSTALL_SNAPSHOTS);
                }
            }
            
            // Workaround for too bold relocation mechanism - corresponding details at https://issues.apache.org/jira/browse/MSHADE-156
            final Configuration initcfg = new Configuration("org.apache.sling.jcr.packageinit.impl.ExecutionPlanRepoInitializer");
            initcfg.getProperties().put("executionplans", executionPlans.toArray(new String[executionPlans.size()]));
            initcfg.getProperties().put("statusfilepath", registryHome.getAbsolutePath() + File.separator + "executedplans.file");
            initcfg.getProperties().put("reinstallSnapshots", anyPlanIncludesSnapshots && reinstallSnapshots);
            context.addConfiguration(initcfg.getPid(), null, initcfg.getProperties());
            // Workaround for too bold relocation mechanism - corresponding details at https://issues.apache.org/jira/browse/MSHADE-156
            final Configuration registrycfg = new Configuration("org.UNSHADE.apache.jackrabbit.vault.packaging.registry.impl.FSPackageRegistry");
            registrycfg.getProperties().put("homePath", registryHome.getPath());
            context.addConfiguration(registrycfg.getPid(), null, registrycfg.getProperties());

            return true;
        } else {
            return false;
        }
    }

    private File getRegistryHomeDir(ExtensionContext context) {
        //read repository- home from framework properties (throw exception if repo.home not set)
        String registryPath = System.getProperty(PACKAGEREGISTRY_HOME);
        File registryHome;
        if (registryPath != null) {
            registryHome = Paths.get(registryPath).toFile();

        } else {
            String repoHome = context.getFrameworkProperties().get(REPOSITORY_HOME);
            if (repoHome == null) {
                throw new IllegalStateException("Neither registry.home set nor repository.home configured.");
            }
            registryHome = Paths.get(repoHome, REGISTRY_FOLDER).toFile();
        }
        if (!registryHome.exists()) {
            registryHome.mkdirs();
        }
        if (!registryHome.isDirectory()) {
            throw new IllegalStateException("Registry home points to file - must be directory: " + registryHome);
        }
        return registryHome;
    }
    
    static class PackageReference {
        private File file;
        private boolean isSnapshot;
        
        public PackageReference(File file, Artifact artifact) {
            this.file = file;
            this.isSnapshot = artifact.getId().getVersion().endsWith("-SNAPSHOT");
        }
    }
    
    static class ExecutionPlanBuilderWithDetails {
        private ExecutionPlanBuilder builder;
        private boolean includesSnapshots;
        
        public ExecutionPlanBuilderWithDetails(ExecutionPlanBuilder builder, boolean includesSnapshots) {
            this.builder = builder;
            this.includesSnapshots = includesSnapshots;
        }
    }
}
