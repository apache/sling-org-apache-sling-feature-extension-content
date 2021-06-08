[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-feature-extension-content/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-feature-extension-content/job/master/)&#32;[![Test Status](https://img.shields.io/jenkins/tests.svg?jobUrl=https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-feature-extension-content/job/master/)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-feature-extension-content/job/master/test/?width=800&height=600)&#32;[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-feature-extension-content&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-feature-extension-content)&#32;[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-feature-extension-content&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-feature-extension-content)&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.feature.extension.content.svg)](https://www.javadoc.io/doc/org.apache.sling/org-apache-sling-feature-extension-content)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.feature.extension.content/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.feature.extension.content%22)&#32;[![feature](https://sling.apache.org/badges/group-feature.svg)](https://github.com/apache/sling-aggregator/blob/master/docs/group/feature.md) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

# Apache Sling Featuremodel - Content Deployment Extension

This module is part of the [Apache Sling](https://sling.apache.org) project.

This module extends the Sling Featuremodel Launcher with [content package](https://jackrabbit.apache.org/filevault/index.html) capabilities. The format of the extension is described in [Extension Content-Packages](https://github.com/apache/sling-org-apache-sling-feature/blob/master/docs/extensions.md#built-in-extension-content-packages).

To influence the package installation order the following format must be used

```
"content-packages:ARTIFACTS|required":[
    "id":"org.apache.sling.myapp:my-content-package:zip:1.0.0"
    "start-order": 1
]
```
The default start order is the maximum integer value (i.e. the package is processed after all packages with an explicit start-order).

All content-packages from the model are registered (externally) in a [`FSPackageRegistry`](https://jackrabbit.apache.org/filevault/apidocs/org/apache/jackrabbit/vault/packaging/registry/impl/FSPackageRegistry.html) and scheduled for installation with execution plans (given via OSGi configuration) which automatically get executed during repository start by [ExecutionPlanRepoInitializer](https://github.com/apache/sling-org-apache-sling-jcr-packageinit/blob/master/src/main/java/org/apache/sling/jcr/packageinit/impl/ExecutionPlanRepoInitializer.java)
