#!/usr/bin/env groovy

/** Jenkinsfile libraries cache */
LinkedHashMap libs = [:]

/** Modules within this mono-repository. Must equal to the directories in root. */
Set<String> modules = ['backend-api', 'webclient', 'identity-provider'] as Set<String>

/** What is being build */
enum BuildSubject {
  MAIN, FEATURE, RELEASE, HOTFIX, NOTHING
}

/** Values will be assigned in the 'Changes' stage */
BuildSubject buildSubject
Set<String> changedFiles // changed files according to git diff (different depending on the buildSubject)
Set<String> changedModules // based on modules and changes in directories

/** Value will be assigned in the 'Actions' stage */
Set<String> requiredStages // based on buildSubject

/** Jenkins Build Pipeline */
pipeline {
  /** Agent */
  agent {
    kubernetes {
      inheritFrom 'ci-default'
      defaultContainer 'jnlp'
    }
  }

  /** Options */
  options {
    disableConcurrentBuilds()
    timeout(time: 15, unit: 'MINUTES')
  }

  triggers {
    bitbucketPush()
  }

  /** Pipeline Stages */
  stages {
    /** Determine what is being build: main, feature, release */
    stage('Subject') {
      steps {
        script {
          def git = load(libs, 'git')
          def changes = load(libs, 'changes')

          echo "Branch: ${env.BRANCH_NAME}"
          echo "Commit: ${env.GIT_COMMIT}"
          echo "Tag: ${git.currentTag()}"

          /* determine build subject */
          buildSubject = changes.guardRelease(
            changes.determineSubject(env.BRANCH_NAME),
            { git.isReleaseTag(git.currentTag()) },
            { echo "Release must have a Release-Tag. Build aborted."; abortBuild() }
          )
          echo "Build subject: $buildSubject"
        }
      }
    }

    /** Determine changes and required stages */
    stage('Pre') {
      when { expression { notNothing(buildSubject) } }
      parallel {
        stage('Changes') { /** Changed Files & Modules */
          steps {
            script {
              def git = load(libs, 'git')
              def changes = load(libs, 'changes')

              /* determine changed files */
              changedFiles = changes.determineChangedFiles(buildSubject, git)
              echo "Changed files: $changedFiles"

              /* determine changed modules */
              changedModules = changes.determineChangedModules(modules, changedFiles)
              echo "Changed modules: $changedModules"
            }
          }
        }

        stage('Required Stages') { /** Required Pipeline Stages */
          steps {
            script {
              def git = load(libs, 'git')
              def changes = load(libs, 'changes')

              /* determine required stages */
              requiredStages = allActiveKeys([
                'integration': { notNothing(buildSubject) },
                'compile'    : { buildSubject in [BuildSubject.MAIN, BuildSubject.FEATURE, BuildSubject.HOTFIX] },
                'checks'     : { buildSubject in [BuildSubject.MAIN, BuildSubject.FEATURE, BuildSubject.HOTFIX] },
//                'delivery'   : { buildSubject in [BuildSubject.MAIN] },
                'deployment' : { buildSubject in [BuildSubject.MAIN, BuildSubject.RELEASE] || changes.isHotfixWith(buildSubject, { git.isReleaseTag(gitTag) }) }
              ])
              echo "Required stages: $requiredStages"
            }
          }
        }
      }
    }

    /** Integration of Changed Modules */
    stage('Integration') {
      when { expression { stageName() in requiredStages } }
      parallel {
        stage('Backend-Api') {
          when { expression { stageName() in changedModules } }
          environment {
            MODULE = "${stageName()}"
          }
          stages {
            stage('Dependencies') {
              steps {
                echo "run install $MODULE/"
              }
            }
            stage('Compile') {
              when { expression { stageName() in requiredStages } }
              steps {
                echo "run build $MODULE/"
              }
            }
            stage('Checks') {
              when { expression { stageName() in requiredStages } }
              steps {
                echo "run test $MODULE/"
              }
              post {
                always {
                  echo 'publish results'
                }
              }
            }
          }
        }
        stage('Webclient') {
          when { expression { stageName() in changedModules } }
          environment {
            MODULE = "${stageName()}"
          }
          stages {
            stage('Dependencies') {
              steps {
                dir("$MODULE") { echo 'run install' }
              }
            }
            stage('Compile') {
              when { expression { stageName() in requiredStages } }
              steps {
                dir("$MODULE") { echo 'run build' }
              }
            }
            stage('Checks') {
              when { expression { stageName() in requiredStages } }
              steps {
                dir("$MODULE") { echo 'run test' }
              }
              post {
                always {
                  echo 'publish results'
                }
              }
            }
          }
        }
      }
    }

    /** Deployment of Changed Modules */
    stage('Deployment') {
      when { expression { stageName() in requiredStages } }
      parallel {
        stage('Backend-Api') {
          when { expression { stageName() in changedModules } }
          environment {
            MODULE = "${stageName()}"
          }
          stages {
            stage('App') {
              when {
                expression { deliveryIsNeeded(requiredStages) }
              }
              steps {
                echo 'build optimized'
              }
            }
            stage('Docker Image') {
              when {
                expression { deliveryIsNeeded(requiredStages) }
              }
              steps {
                echo 'executor build'
              }
            }
            stage('Deployment') {
              steps {
                dir('backend-api/infrastructure') {
                  echo 'terraform apply'
                }
              }
            }
          }
        }
        stage('Webclient') {
          when { expression { stageName() in changedModules } }
          environment {
            MODULE = "${stageName()}"
          }
          stages {
            stage('App') {
              when {
                expression { deliveryIsNeeded(requiredStages) }
              }
              steps {
                dir('webclient') { echo 'build optimized' }
              }
            }
            stage('Docker Image') {
              when {
                expression { deliveryIsNeeded(requiredStages) }
              }
              steps {
                echo 'executor build'
              }
            }
            stage('Deployment') {
              steps {
                dir('webclient/infrastructure') {
                  echo 'terraform apply'
                }
              }
            }
          }
        }
        stage('Identity-Provider') {
          when { expression { stageName() in changedModules } }
          stages {
            stage('Deployment') {
              steps {
                dir('identity-provider/infrastructure') {
                  echo 'terraform apply'
                }
              }
            }
          }
        }
      }
    }
  }

  /** Post Notifications */
  post {
    unsuccessful {
      script { notifyTeam() }
    }
    fixed {
      script { notifyTeam() }
    }
  }
}

/** Loads a library file and caches it in libs */
def load(LinkedHashMap libs, String libName) {
  if (!(libName in libs.keySet())) {
    libs[libName] = load "Jenkinsfile.${libName}.groovy"
  }
  return libs[libName]
}

/** Aborts the current build */
void abortBuild() {
  currentBuild.result = 'ABORTED'
}

/** @return true iff buildSubject is not nothing and not null  */
boolean notNothing(BuildSubject buildSubject) {
  return !(buildSubject in [null, BuildSubject.NOTHING])
}

/** @return the keys of map where the associated condition yields true  */
Set<String> allActiveKeys(LinkedHashMap<String, Closure<Boolean>> map) {
  return map.findAll { _, c -> c() }.keySet()
}

/** @return lowercase stage name  */
String stageName() {
  return STAGE_NAME?.toLowerCase()
}

/** @return whenever delivery is required; or needed by deployment  */
boolean deliveryIsNeeded(Set<String> requiredStages) {
  return ['delivery', 'deployment'].any { it in requiredStages }
}

/** Notifies the dev team */
void notifyTeam() {
  echo "send notification!"
}