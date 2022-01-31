#!/usr/bin/env groovy

/**
 * @param branch - current branch
 * @return the build subject determined by the current branch or NOTHING is none match
 */
BuildSubject determineSubject(String branch) {
  switch (branch) {
    case 'main': return BuildSubject.MAIN
    case ~/(feature|bugfix)\/(.+)/: return BuildSubject.FEATURE
    case ~/release\\/(.+)/: return BuildSubject.RELEASE
    case ~/hotfix\\/(.+)/: return BuildSubject.HOTFIX
  }
  return BuildSubject.NOTHING
}

/**
 * @param buildSubject - current buildSubject
 * @param requirement - RELEASE requirement
 * @param onAbort - call when RELEASE is aborted
 * @return NONE when requirement for RELEASE is not met, otherwise given buildSubject
 */
BuildSubject guardRelease(BuildSubject buildSubject, Closure<Boolean> requirement, Closure onAbort) {
  if(buildSubject == BuildSubject.RELEASE && !requirement()) {
    onAbort()
    return BuildSubject.NOTHING
  }
  return buildSubject
}

/**
 * @param buildSubject - current buildSubject
 * @param requirement - requirement for hotfix
 * @return true iff buildSubject is a hotfix and satisfies the requirement
 */
boolean isHotfixWith(BuildSubject buildSubject, Closure<Boolean> requirement) {
  return buildSubject == BuildSubject.HOTFIX && requirement()
}

/**
 * @param buildSubject
 * @param git - the git functions
 * @return all changed files according to git diff based on buildSubject
 */
List<String> determineChangedFiles(BuildSubject buildSubject, def git) {
  switch (buildSubject) {
    case BuildSubject.MAIN:
      return git.changes()
    case BuildSubject.FEATURE:
      return git.changes(git.remoteBranch('main'))
    case [BuildSubject.RELEASE, BuildSubject.HOTFIX]:
      String prevRelease = git.precedingReleaseTag()
      echo "Preceding release: $prevRelease"
      return git.changes(prevRelease)
  }
  return [] as List<String>
}

/**
 * @param modules - all modules
 * @param changedFiles - all changed files
 * @return the changed modules based on root dirs of changedFiles
 */
Set<String> determineChangedModules(Set<String> modules, Set<String> changedFiles) {
  Set<String> dirs = dirs(changedFiles)
  return modules.findAll { it in dirs }
}

/**
 * @param paths - a list of paths
 * @param maxDepth - max directory depth (default = 1)
 * @return set of directories in paths; root dir is denoted with './'
 */
@NonCPS
Set<String> dirs(Set<String> paths, Integer maxDepth = 1) {
  assert maxDepth >= 1
  return paths*.split('/')*.toList()*.dropRight(1).collect { !it ? ['./'] : it }*.take(maxDepth)*.join('/').toSet()
}

return this