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
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
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
    static final String SKIP_EXECUTIONPLANS_MSG = "ContentHandler set up to skip building of executionplans - only configuring FSPackageRegistry";
    public static final String PACKAGEREGISTRY_HOME = "packageregistry.home";
    public static final String SKIP_EXECUTIONPLANS_PROP = "skipxecutionplans";

    private static final String REPOSITORY_HOME = "repository.home";

    private static final String REGISTRY_FOLDER = "packageregistry";

    static ExecutionPlanBuilder buildExecutionPlan(Collection<Artifact> artifacts, Set<PackageId> satisfiedPackages, LauncherPrepareContext prepareContext, File registryHome) throws IOException, PackageException  {

        List<File> packageReferences = new ArrayList<>();

        for (final Artifact a : artifacts) {
            final URL file = prepareContext.getArtifactFile(a.getId());
            File tmp = IOUtils.getFileFromURL(file, true, null);
            if (tmp != null) {
                packageReferences.add(tmp);
            }
        }

        if (!registryHome.exists()) {
            registryHome.mkdirs();
        }

        FSPackageRegistry registry = new FSPackageRegistry(registryHome, InstallationScope.UNSCOPED, new SecurityConfig(null, null), true);

        ExecutionPlanBuilder builder = registry.createExecutionPlan();
        builder.with(satisfiedPackages);

        for (File pkgFile : packageReferences) {
            try {
                PackageId pid = registry.registerExternal(pkgFile, false);
                ImportOptions importOptions = new ImportOptions();
                importOptions.setStrict(true);
                PackageTaskOptions options = new ImportOptionsPackageTaskOption(importOptions);
                builder.addTask().with(pid).withOptions(options).with(Type.EXTRACT);
            } catch (PackageExistsException ex) {
                // Expected - the package is already present
            }
        }
        builder.validate();
        satisfiedPackages.addAll(builder.preview());
        return builder;

    }

    @Override
    public boolean handle(ExtensionContext context, Extension extension) throws Exception {
        File registryHome = getRegistryHomeDir(context);
        if (extension.getType() == ExtensionType.ARTIFACTS
                && extension.getName().equals(Extension.EXTENSION_NAME_CONTENT_PACKAGES)) {
            if(skipBuildExecutionPlans()){
                context.getLogger().info(SKIP_EXECUTIONPLANS_MSG);
            } else {
                List<String> executionPlans = new ArrayList<>();
                Map<Integer, Collection<Artifact>> orderedArtifacts = getOrderedArtifacts(extension);
                Set<PackageId> satisfiedPackages = new HashSet<>();
                for (Collection<Artifact> artifacts : orderedArtifacts.values()) {
                    ExecutionPlanBuilder builder = buildExecutionPlan(artifacts, satisfiedPackages, context, registryHome);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    builder.save(baos);
                    executionPlans.add(baos.toString("UTF-8"));
                }
                configurePackageInit(context, registryHome, executionPlans);
            }
            configureFSRegistry(context, registryHome);
            return true;
        } else {
            return false;
        }
    }

    private Map<Integer, Collection<Artifact>> getOrderedArtifacts(Extension extension) {
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
        return orderedArtifacts;
    }

    private void configureFSRegistry(ExtensionContext context, File registryHome) {
         // Workaround for too bold relocation mechanism - corresponding details at https://issues.apache.org/jira/browse/MSHADE-156
        final Configuration registrycfg = new Configuration("org.UNSHADE.apache.jackrabbit.vault.packaging.registry.impl.FSPackageRegistry");
        registrycfg.getProperties().put("homePath", registryHome.getPath());
        context.addConfiguration(registrycfg.getPid(), null, registrycfg.getProperties());
    }

    private void configurePackageInit(ExtensionContext context, File registryHome, List<String> executionPlans) {
         // Workaround for too bold relocation mechanism - corresponding details at https://issues.apache.org/jira/browse/MSHADE-156
        final Configuration initcfg = new Configuration("org.apache.sling.jcr.packageinit.impl.ExecutionPlanRepoInitializer");
        initcfg.getProperties().put("executionplans", executionPlans.toArray(new String[executionPlans.size()]));
        initcfg.getProperties().put("statusfilepath", registryHome.getAbsolutePath() + "/executedplans.file");
        context.addConfiguration(initcfg.getPid(), null, initcfg.getProperties());
    }

    private boolean skipBuildExecutionPlans() {
        return Boolean.getBoolean(SKIP_EXECUTIONPLANS_PROP);
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
}
