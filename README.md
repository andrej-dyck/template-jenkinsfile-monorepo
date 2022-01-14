# Monorepo Jenkinsfile Template

A Jenkinsfile template 📄 for a _monorepo_ project 👨‍💻, which allows for its modules to be integrated ⚙ and deployed 📦 _independently_.

## Motivation and Background
[Jenkins](https://www.jenkins.io/) is a popular build-pipeline choice for companies as it is open-source and companies can introduce their own agents. 

Further, e.g., web apps tend to be developed in a [monorepo](https://en.wikipedia.org/wiki/Monorepo) as their _parts_ (e.g., _api_ and _client_; I'll refer to them as _modules_) are usually changed together; thus, teams can benefit from some advantages of monorepos.

However, there are many changes that touch only certain parts of the system; e.g., refactoring code, writing documentation, fixing translations, changing infrastructure. For these, we need **independent** builds and deployments.

Jenkins is not capable to provide useful _change sets_; its implementation of `changeset` is based on previous build, and thus, does not work for `feature` and `release` branches, or when working with [rebase](https://git-scm.com/docs/git-rebase) (cf. [clean history](https://github.com/andrej-dyck/git-kata)).

Thus, this repository comprises a Jenkinsfile ([declarative pipeline](https://www.jenkins.io/doc/book/pipeline/syntax/)) that can identify changes via `git diff` depending on what is built, and then, _integrate_ (compile and test) and _deliver/deploy_ changed modules independently. 

## Context and Quirks
This Jenkinsfile template is used in a project where we follow a (slightly adapted) [trunk-based development workflow](https://trunkbaseddevelopment.com/).

![Trunk-based Git Workflow](./assets/trunk-based-development.png)

First thing to note is that `feature` branches are always rebased onto `main` before merge. Further, the merge into `main` is done via a merge commit (while keeping the linear history); this gives us a nice visual group of commits that belong together.

Furthermore, `release` branches are used for releases; as they provide the possibility to prepare a release[^1] and [cherry pick](https://git-scm.com/docs/git-cherry-pick) fixes when needed. However, releases are built only when the HEAD has a valid _release tag_; this way we don't forget to tag the release.

[^1]: In our project, changelogs are mandatory by law and must exist during start of the application.

### Defining Changes
The changes are determined depending on what is being built (aka on which branch we are).
* On `main` (as we work with merge commits), the diff are the changes to the _previous commit_ (i.e., _HEAD~1_) on `main`.
* On `feature` (and `bugfix`), the diff are the changes between the current branch and `main`.
* On `release`, the diff are the changes since the previous release (i.e., last _release tag_ before the current one)

### Deployment vs Delivery
In our workflow, all changes in `main` are deployed to a _staging_ environment (the delivery of a release candidate). Then, _releases_ are deployed to the _production_ environment.

Note: since all modules should be deployable independently, they need to have their own infrastructure as code specifications. 

### Why not use Jenkins PR/Tags? 
We found that Jenkins _PR-_ and _Tag discovery_ is flaky and unreliable (especially when working with rebase). So, with this Jenkinsfile, we discover only named branches and determine changes ourselves. Further, those do not solve Jenkins' issue with the change set. 

## Jenkins Settings

TBD