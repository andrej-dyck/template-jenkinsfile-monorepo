#!/usr/bin/env groovy

/**
 * @param base - the base commit to compare to (default: previous commit on this branch)
 * @return list of all file changes
 */
Set<String> changes(String base = "${remoteHead()}~1".toString()) {
  return sh(
    returnStdout: true,
    script: "git --no-pager diff --name-only $base"
  ).readLines() as Set<String>
}

/**
 * @return the reference of remote head for the current branch,
 * as jenkins does not check out a local branch
 */
String remoteHead() {
  return remoteBranch(env.GIT_BRANCH)
}

/**
 * @return the reference of remote main
 */
String remoteBranch(String branchName) {
  return "remotes/origin/$branchName".toString()
}

/**
 * @return the tag of HEAD or 'undefined'
 */
String currentTag() {
  String tag = sh(
    returnStdout: true,
    script: "git tag --points-at ${remoteHead()} | head -n 1"
  ).trim()
  return tag.allWhitespace ? 'undefined' : tag
}

/**
 * @param tag - a git tag
 * @return true iff tag matches the release-tag pattern
 */
boolean isReleaseTag(String tag) {
  return tag =~ /Release-\d+(\.\d+(\.\d+)?)?/
}

/**
 * @return the preceding release tag or 'undefined'
 */
String precedingReleaseTag() {
  String gitTag = currentTag()
  def (lastTag, secondButLastTag, _) = releaseTags(2)
  return (gitTag == 'undefined' ? lastTag : secondButLastTag) ?: 'undefined'
}

/**
 * @param releaseTagERE - the unix extended regex expression (ERE) for release tags
 * @return list of all release tags in reverse order
 */
List<String> releaseTags(int take = -1, String releaseTagERE = "^Release-[0-9]+(\\.[0-9]+(\\.[0-9]+)?)?\$") {
  return sh(
    returnStdout: true,
    script: "git tag | grep -E \"$releaseTagERE\" | sort -V -r" + (take > 0 ? " | head -n $take" : '')
  ).readLines()
}

return this