#!/usr/bin/env groovy

/** Modules within this mono-repository. Must equal to the directory in root. */
Set<String> modules = ['backend-api', 'webclient', 'identity-provider'] as Set<String>

/** What is being build */
enum BuildSubject {
  MAIN, FEATURE, RELEASE, NOTHING
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
      inheritFrom 'default'
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
          def git = load 'Jenkinsfile.Git.groovy'
          def changes = load 'Jenkinsfile.Changes.groovy'

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
              def git = load 'Jenkinsfile.Git.groovy'
              def changes = load 'Jenkinsfile.Changes.groovy'

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
              /* determine required stages */
              requiredStages = allTruthyKeys([
                'integration': { notNothing(buildSubject) },
                'compile'    : { buildSubject in [BuildSubject.MAIN, BuildSubject.FEATURE] },
                'checks'     : { buildSubject in [BuildSubject.MAIN, BuildSubject.FEATURE] },
                'deployment' : { buildSubject in [BuildSubject.MAIN, BuildSubject.RELEASE] }
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
          stages {
            stage('Dependencies') {
              steps {
                echo 'run install'
              }
            }
            stage('Compile') {
              when { expression { stageName() in requiredStages } }
              steps {
                echo 'run build'
              }
            }
            stage('Checks') {
              when { expression { stageName() in requiredStages } }
              steps {
                echo 'run test'
              }
            }
          }
        }
        stage('Webclient') {
          when { expression { stageName() in changedModules } }
          stages {
            stage('Dependencies') {
              steps {
                echo 'run install'
              }
            }
            stage('Compile') {
              when { expression { stageName() in requiredStages } }
              steps {
                echo 'run build'
              }
            }
            stage('Checks') {
              when { expression { stageName() in requiredStages } }
              steps {
                echo 'run test'
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
          stages {
            stage('App') {
              steps {
                echo 'build optimized'
              }
            }
            stage('Docker Image') {
              steps {
                echo 'docker build'
              }
            }
            stage('Deployment') {
              steps {
                echo 'terraform apply'
              }
            }
          }
        }
        stage('Webclient') {
          when { expression { stageName() in changedModules } }
          stages {
            stage('App') {
              steps {
                echo 'build optimized'
              }
            }
            stage('Docker Image') {
              steps {
                echo 'docker build'
              }
            }
            stage('Deployment') {
              steps {
                echo 'terraform apply'
              }
            }
          }
        }
        stage('Identity-Provider') {
          when { expression { stageName() in changedModules } }
          stages {
            stage('Deployment') {
              steps {
                echo 'terraform apply'
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
      echo "send notification!"
    }
    fixed {
      echo "send notification!"
    }
  }
}

void abortBuild() {
  currentBuild.result = 'ABORTED'
}

String stageName() {
  return STAGE_NAME?.toLowerCase()
}

boolean notNothing(BuildSubject buildSubject) {
  return !(buildSubject in [null, BuildSubject.NOTHING])
}

Set<String> allTruthyKeys(LinkedHashMap<String, Closure<Boolean>> map) {
  return map.findAll { _, c -> c() }.collect { it.key } as Set<String>
}