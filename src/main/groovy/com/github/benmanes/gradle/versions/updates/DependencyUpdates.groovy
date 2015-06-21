/*
 * Copyright 2012-2014 Ben Manes. All Rights Reserved.
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
package com.github.benmanes.gradle.versions.updates

import groovy.transform.TupleConstructor
import groovy.transform.TypeChecked
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

import static groovy.transform.TypeCheckingMode.SKIP
import static org.gradle.api.specs.Specs.SATISFIES_ALL

/**
 * An evaluator for reporting of which dependencies have later versions.
 * <p>
 * The <tt>revision</tt> property controls the resolution strategy:
 * <ul>
 *   <li>release: selects the latest release
 *   <li>milestone: select the latest version being either a milestone or a release (default)
 *   <li>integration: selects the latest revision of the dependency module (such as SNAPSHOT)
 * </ul>
 *
 * @author Ben Manes (ben.manes@gmail.com)
 */
@TypeChecked
@TupleConstructor
class DependencyUpdates {
  Project project
  String revision
  Object outputFormatter
  String outputDir

  /** Evaluates the dependencies and returns a reporter. */
  DependencyUpdatesReporter run() {
    Resolver resolver = new Resolver(project)
    List<Configuration> configurations = project.allprojects.collectMany { proj ->
      proj.configurations.plus(proj.buildscript.configurations)
    }
    Set<DependencyStatus> status = configurations.collectMany { configuration ->
      resolver.resolve(configuration, revision)
    } as Set

    VersionMapping versions = new VersionMapping(project, status)
    Set<UnresolvedDependency> unresolved =
      status.findAll { it.unresolved != null }.collect { it.unresolved } as Set
    return createReporter(versions, unresolved)
  }

  private DependencyUpdatesReporter createReporter(
      VersionMapping versions, Set<UnresolvedDependency> unresolved) {
    Map<Map<String, String>, String> currentVersions =
      versions.current.collectEntries { [[group: it.groupId, name: it.artifactId]: it.version] }
    Map<Map<String, String>, String> latestVersions =
      versions.latest.collectEntries { [[group: it.groupId, name: it.artifactId]: it.version] }
    Map<Map<String, String>, String> upToDateVersions =
      versions.upToDate.collectEntries { [[group: it.groupId, name: it.artifactId]: it.version] }
    Map<Map<String, String>, String> downgradeVersions =
      versions.downgrade.collectEntries { [[group: it.groupId, name: it.artifactId]: it.version] }
    Map<Map<String, String>, String> upgradeVersions =
      versions.upgrade.collectEntries { [[group: it.groupId, name: it.artifactId]: it.version] }
    return new DependencyUpdatesReporter(project, revision, outputFormatter, outputDir,
      currentVersions, latestVersions, upToDateVersions, downgradeVersions, upgradeVersions, unresolved)
  }
}
