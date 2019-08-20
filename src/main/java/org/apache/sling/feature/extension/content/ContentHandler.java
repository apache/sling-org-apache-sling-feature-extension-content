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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.collections.map.MultiValueMap;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlanBuilder;
import org.apache.jackrabbit.vault.packaging.registry.PackageTask.Type;
import org.apache.jackrabbit.vault.packaging.registry.impl.FSPackageRegistry;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.io.IOUtils;
import org.apache.sling.feature.launcher.spi.LauncherPrepareContext;
import org.apache.sling.feature.launcher.spi.extensions.ExtensionContext;
import org.apache.sling.feature.launcher.spi.extensions.ExtensionHandler;

public class ContentHandler implements ExtensionHandler {
    public static final String PACKAGEREGISTRY_HOME = "packageregistry.home";

    private static final String REPOSITORY_HOME = "repository.home";

    private static final String REGISTRY_FOLDER = "packageregistry";

    private static ExecutionPlanBuilder buildExecutionPlan(Collection<Artifact> artifacts, Set<PackageId> satisfiedPackages, LauncherPrepareContext prepareContext, File registryHome) throws Exception {

        List<File> packageReferences = new ArrayList<>();

        for (final Artifact a : artifacts) {
            final URL file = prepareContext.getArtifactFile(a.getId());
            File tmp = IOUtils.getFileFromURL(file, true, null);

            if (tmp.length() > 0)
            {
                packageReferences.add(tmp);
            }
        }

        if(!registryHome.exists()) {
            registryHome.mkdirs();
        }

        FSPackageRegistry registry = new FSPackageRegistry(registryHome);

        ExecutionPlanBuilder builder = registry.createExecutionPlan();
        builder.with(satisfiedPackages);

        for (File pkgFile : packageReferences) {

            PackageId pid = registry.registerExternal(pkgFile, true);
            extractSubPackages(registry, builder, pid);

            builder.addTask().with(pid).with(Type.EXTRACT);
        }
        builder.validate();
        satisfiedPackages.addAll(builder.preview());
        return builder;

    }

    private static void extractSubPackages(FSPackageRegistry registry, ExecutionPlanBuilder builder, PackageId pid)
            throws IOException {
        Map<PackageId, SubPackageHandling.Option> subPkgs = registry.getInstallState(pid).getSubPackages();
        if (!subPkgs.isEmpty()) {
            for (PackageId subId : subPkgs.keySet()) {
                SubPackageHandling.Option opt = subPkgs.get(subId);
                if (opt != SubPackageHandling.Option.IGNORE) {
                    builder.addTask().with(subId).with(Type.EXTRACT);
                    extractSubPackages(registry, builder, subId);
                }
            }
        }
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean handle(ExtensionContext context, Extension extension) throws Exception {
        File registryHome = getRegistryHomeDir(context);
        if (extension.getType() == ExtensionType.ARTIFACTS
                && extension.getName().equals(Extension.EXTENSION_NAME_CONTENT_PACKAGES)) {
            MultiValueMap orderedArtifacts = MultiValueMap.decorate(new TreeMap<Integer, Collection<Artifact>>());
            for (final Artifact a : extension.getArtifacts()) {
                int order;
                // content-packages without explicit start-order to be installed last
                if (a.getMetadata().get(Artifact.KEY_START_ORDER) != null) {
                    order = a.getStartOrder();
                } else {
                    order = Integer.MAX_VALUE;
                }
                orderedArtifacts.put(order, a);
            }
            List<String> executionPlans = new ArrayList<String>();
            Set<PackageId> satisfiedPackages = new HashSet<>();
            for (Object key : orderedArtifacts.keySet()) {
                @SuppressWarnings("unchecked")
                Collection<Artifact> artifacts = orderedArtifacts.getCollection(key);
                ExecutionPlanBuilder builder = buildExecutionPlan(artifacts, satisfiedPackages, context, registryHome);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                builder.save(baos);
                executionPlans.add(baos.toString("UTF-8"));
            }
            // Workaround for too bold relocation mechanism - corresponding details at https://issues.apache.org/jira/browse/MSHADE-156
            final Configuration initcfg = new Configuration("org.UNSHADE.apache.sling.jcr.packageinit.impl.ExecutionPlanRepoInitializer");
            initcfg.getProperties().put("executionplans", executionPlans.toArray(new String[executionPlans.size()]));
            initcfg.getProperties().put("statusfilepath", registryHome.getAbsolutePath() + "/executedplans.file");
            context.addConfiguration(initcfg.getPid(), null, initcfg.getProperties());
            // Workaround for too bold relocation mechanism - corresponding details at https://issues.apache.org/jira/browse/MSHADE-156
            final Configuration registrycfg = new Configuration("org.UNSHADE.apache.jackrabbit.vault.packaging.registry.impl.FSPackageRegistry");
            registrycfg.getProperties().put("homePath", registryHome.getPath());
            context.addConfiguration(registrycfg.getPid(), null, registrycfg.getProperties());

            return true;
        }
        else {
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
            throw new IllegalStateException("Registry but points to file - must be directory");
        }
        return registryHome;
    }
}
