# bitbucket-pipelines-cli

A CLI to integrate into BitBucket pipelines

The official Meterian Scanner CLI client for Bitbucket Pipelines.

This has not been published yet, but you can have a look at the code and snoop around. This plugin is expected to work with Bitbucket pipelines (bitbucket-pipelines.yml) with the ability to include the analysis as part of your build process, as it should be.

The integration that we are building first, also in light that nobody did that before, is with [Gerrit Code Review](https://www.gerritcodereview.com/), where we with the help of [Robot Comments](https://www.gerritcodereview.com/config-robot-comments.html) we can leverage the automatic fixing ability of Meterian with the smooth review flow of Gerrit.

More to be written here after the CLI client is launched :)


## Build

### Local

Build the project with the below command from inside the project root folder:

```bash
mvn package
```

Note: this runs the unit/integration tests which is part of the project and can take a bit of time.

Tests it runs:

Running InjectedTest:

- MeterianClientAutofixFeatureTest

Look in the `target` folder for all the generate artifacts, you will find `bitbucket-pipelines-cli-jar-with-dependencies.jar` among others.

### Docker

Same as the above except available in an isolated environment.

```bash
[from project root folder]
cd docker
./buildDockerContainer.sh
``` 

## Run

### Local

Run the project with the below command from inside the project root folder:

```bash
java -jar target/bitbucket-pipelines-cli-jar-with-dependencies.jar [Meterian args]

for e.g.
    java -jar target/bitbucket-pipelines-cli-jar-with-dependencies.jar --autofix
```

```
[meterian args] - one or more args for additional features i.e. `--interactive=false` or `--autofix`
```

This kicks off a the meterian scanner client which does the analysis of the current project and then depending on the Meterian client arguments passed.

On the first run, it will authorise you, so please ensure you have an account on https://www.meterian.com, in case you do not have the environment variables necessary to run the above being set up. See [Configuration](README.md#Configuration) section for further details.

_Note: the Meterian client is automatically downloaded by the plugin when it detects the absence of it and is saved in the `${HOME}/.meterian` folder._

### Docker

Same as the above except available in an isolated environment.

```bash
[from project root folder]
cd docker
./runInDockerContainer.sh
``` 

## Artifacts

The ```mvn package``` command run in the `Meterian Scanner Client` module will generate an uber-jar to be found in the `target` folder. This can be used to run analysis in BitBucket Pipelines or from CLI against a Bitbucket git repository. Refer to [Configuration](README.md#Configuration) section for more details.

## Configuration

In order to run the tests locally we would need the following two environment variables populated with valid values or else the tests will fail:

- `METERIAN_API_TOKEN` - this is generated via the Meterian dashboard (via https://www.meterian.com, you will need an account)

### Bitbucket Pipeline users

- `METERIAN_BITBUCKET_USER` - this is the Bitbucket user account created by yourself or someone else in the organisation (for e.g. meterian-bot), it's where all the public and private repositories are
- `METERIAN_BITBUCKET_EMAIL` - this is the email address associated with the Bitbucket user account created in the previous step (for e.g. bot.bitbucket@meterian.io)
- `METERIAN_BITBUCKET_APP_PASSWORD` - this is a password generated from the above user account (learn more about it at [BitBucket: App Passwords](https://confluence.atlassian.com/bitbucket/app-passwords-828781300.html)). Look for _App passwords_ in the account settings (see https://bitbucket.org/account/user/[user account]/).

Note:

 - all the above field are mandatory to be setup via the Bitbucket Account Variables. 
 - for both the `METERIAN_GITHUB_TOKEN` and `METERIAN_BITBUCKET_APP_PASSWORD` please ensure that the scope assigned to the token or password is limited to Pull Request Read and Pull Request Write _only_. Always store them in a secure manner.

### CircleCI configuration

Ensure the above two environment variables are set via https://circleci.com/gh/[Your GitHub Org]/bitbucket-pipelines-cli/edit#env-vars.

_**Note:** in general the above might not be needed if your CI/CD environment contains the necessary permission to perform `git push` related actions on the target repo you are analysing._

## Run Tests 

### All tests

```bash
mvn test
```

### Specific tests

```bash
mvn test -Dtest=MeterianClientAutofixFeatureTest
```

## Meterian features

### Only report

Default behaviour no client args required.

This option does the default task of scanning the project and reporting the vulnerabilities, if present, and recommended fixes but does not apply them.

### Autofix (report and create Pull Request)

Using the `--autofix` client arg option when running the CLI client.

This option does the task of scanning the project and reporting the vulnerabilities, if present, and also applying the fix to the respective branch and creating a pull request to the repository.

Ensure your Bitbucket App Password to your Organisation has been added to the Application Variables section in Bitbucket, see [Configuration](README.md#Configuration).

#### Build status
[![CircleCI](https://circleci.com/gh/MeterianHQ/bitbucket-pipelines-cli.svg?style=svg)](https://circleci.com/gh/MeterianHQ/bitbucket-pipelines-cli)

## Bitbucket Pipelines

Find out more about [Bitbucket Pipelines](https://bitbucket.org/product/features/pipelines) at [Pipes for Bitbucket Pipelines](https://confluence.atlassian.com/bitbucket/how-to-make-a-pipe-for-pipelines-966051288.html).

### Configuration

All you need is a `.yaml` file in the root directory of a repository and bit of one-off settings in the dashboard.

A sample pipeline file can look like:

````yaml
pipelines:
  default:
    - step:
        script:
          - echo 'I made a pipeline!'
````

Have a look at a real [Bitbucket Pipeline config](https://bitbucket.org/meterian-bot/clientofmutabilitydetector/src/master/bitbucket-pipelines.yml) file and see what would you have to do in order to use the Meterian Scanner Client CLI tool to scan your project each time a commit was pushed.