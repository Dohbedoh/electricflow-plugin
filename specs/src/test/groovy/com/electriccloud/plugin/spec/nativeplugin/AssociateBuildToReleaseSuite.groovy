package com.electriccloud.plugin.spec.nativeplugin

import com.electriccloud.plugin.spec.JenkinsHelper
import com.electriccloud.plugin.spec.core.cibuilddetails.*
import com.electriccloud.plugin.spec.core.pipeline.PipelineRun
import com.electriccloud.plugin.spec.core.release.Release
import com.electriccloud.plugin.spec.nativeplugin.utils.JenkinsBuildJob
import com.electriccloud.plugin.spec.nativeplugin.utils.JenkinsJobRunner
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Unroll

class AssociateBuildToReleaseSuite extends JenkinsHelper {

    public static final String testPbaName = "cloudBeesFlowAssociateBuildToRelease"
    private static final String testProjectName = "Specs - electricflow-plugin - $testPbaName"

    private static final String PIPELINE_NAME = "nativeJenkinsPBAExtendedPipelineProject"
    public static final String CI_CONFIG_NAME = "electricflow"

    private static JenkinsJobRunner jjr = JenkinsJobRunner.getInstance()

    @Shared
    String pbaToRun, fullArtifact, flowRuntimeId, caseId

    static def pbasSequence = [
            only         : testPbaName,
            afterArtifact: 'PublishArtifact, ' + testPbaName,
    ]

    static def projects = [
            correct          : 'pvNativeJenkinsProject01',
            incorrect        : 'incorrect',
            releaseNotStarted: 'pvNativeJenkinsProject02',
            empty            : ''
    ]

    static def releases = [
            correct  : 'pvRelease',
            incorrect: 'incorrect',
            empty    : '',
            associateBuild: 'TriggerReleaseAssociateBuild',
    ]

    private static Release release
    private static PipelineRun pipelineRun

    static def flowRuntimeIds = [
            correct: null, // Should be set in doSetupSpec
            invalid: 'incorrect',
            empty  : ''
    ]

    static def logMessages = [
            noSuchProject  : '"code":"NoSuchProject"',
            noSuchRelease  : '"code":"NoSuchRelease"',
            releaseNotFound: 'No release was found for parameters'
    ]

    def doSetupSpec() {
        release = new Release(projects.correct, releases.correct)
        pipelineRun = release.getReleasePipeline().getLastRun()
        assert pipelineRun != null: "Release should be started"
        flowRuntimeIds['correct'] = pipelineRun.getId() as String
        println("PIPELINE RUN ID: ${flowRuntimeIds['correct']}")

        importJenkinsJob('AssociateBuildToRelease.xml', 'AssociateBuildToRelease')
        dsl(new File(getClass().getResource('/dsl/RunAndWait/runAndWaitRelease.dsl').toURI()).text
                .replace('TriggerReleaseRunAndWait', 'TriggerReleaseAssociateBuild'))
        dslFile('dsl/RunAndWait/runAndWaitProcedure.dsl')
        dsl("""
artifact 'test:AssociateBuildToRelease', artifactKey: 'AssociateBuildToRelease', {
  description = ''
  artifactVersionNameTemplate = ''
  groupId = 'test'
}
""")

    }

    @Unroll
    def "C499948.#caseId. AssociateBuildToRelease - #pbaToRun. FlowRuntimeId: '#flowRuntimeId'"() {
        given: 'Parameters for the pipeline'

        String projectName = projects.correct
        String releaseName = releases.correct

        // Determining where the CIBuildDetail will be attached
        WithCiBuildDetails source = (flowRuntimeId == '') ? release : pipelineRun

        def ciPipelineParameters = [
                flowConfigName : CI_CONFIG_NAME,
                flowProjectName: projectName,
                flowReleaseName: releaseName,
                flowRuntimeId  : flowRuntimeId,
                runOnly        : URLEncoder.encode(pbaToRun, "UTF-8")
        ]

        when: 'Run pipeline and collect run properties'
        JenkinsBuildJob ciJob = jjr.run(PIPELINE_NAME, ciPipelineParameters)

        then: 'Collecting the result objects'
        assert ciJob.isSuccess(): "Pipeline on Jenkins is finished."
        String buildName = ciJob.getJenkinsBuildDisplayName()

        CiBuildDetailInfo ciBuildDetailInfo = source.findCiBuildDetailInfo(buildName)
        CiBuildDetail cbd = ciBuildDetailInfo?.getCiBuildDetail()
        TestResults tr = ciBuildDetailInfo?.getTestResults()
        ArtifactDetails artifact = ciBuildDetailInfo?.getArtifacts()?.get(0)

        if (ciBuildDetailInfo == null)
            System.err.println(ciJob.getFullLogs())

        // Receiving extended information about the CI build details
        then: 'Checking the CiBuildDetail values'
        verifyAll { // soft assert. Will show all the failed assertions
            ciBuildDetailInfo['associationType'] == 'attached'
            ciBuildDetailInfo['result'] == "SUCCESS"
            cbd['buildTriggerSource'] == "CI"
        }

        then: 'Checking inner class values'
        checkTestResults(tr)
        checkArtifactDetails(artifact, fullArtifact == '1')

        where:
        caseId | pbaToRun                   | fullArtifact | flowRuntimeId
        '1'    | pbasSequence.only          | '0'          | flowRuntimeIds.empty
        'xx'   | pbasSequence.only          | '0'          | flowRuntimeIds.correct
        '2'    | pbasSequence.afterArtifact | '1'          | flowRuntimeIds.empty
        'xx'   | pbasSequence.afterArtifact | '1'          | flowRuntimeIds.correct
    }

    @Unroll
    @Issue("NTVEPLUGIN-375")
    def "AssociateBuildToRelease, check different status "() {
        given: 'Parameters for the pipeline'

        // Start release and receive a pipeline data
        def pipelineReleaseInfo = dsl """
                    startRelease(
                        projectName: '${projects.correct}',
                        releaseName: 'TriggerReleaseAssociateBuild',
                        pipelineParameter: [procedureOutcome: 'success', sleepTime: '5'], 
                    )
        """
        waitUntil {
            pipelineCompleted(pipelineReleaseInfo)
        }

        flowRuntimeId = pipelineReleaseInfo.flowRuntime.flowRuntimeId

        String projectName = projects.correct
        String releaseName = releases.associateBuild

        def ciPipelineParameters = [
                flowConfigName : CI_CONFIG_NAME,
                projectName: projectName,
                releaseName: releaseName,
                flowRuntimeId  : flowRuntimeId,
                CIbuildResult: CIbuildResult
        ]

        when: 'Run pipeline and collect run properties'
        JenkinsBuildJob ciJob = jjr.run('AssociateBuildToRelease', ciPipelineParameters)

        then: 'Collecting the result objects'
        assert ciJob.getCiJobOutcome() == CIbuildResult : "Pipeline on Jenkins is finished."

        String buildName = ciJob.getJenkinsBuildDisplayName()
        Release releaseCD = new Release(projects.correct, releases.associateBuild)
        PipelineRun pipelineRunCD = releaseCD.getReleasePipeline().getLastRun()

        CiBuildDetailInfo ciBuildDetailInfo = pipelineRunCD.findCiBuildDetailInfo(buildName)
        CiBuildDetail cbd = ciBuildDetailInfo?.getCiBuildDetail()
        ArtifactDetails artifact = ciBuildDetailInfo?.getArtifacts()?.get(0)

        if (ciBuildDetailInfo == null) {
            System.err.println(ciJob.getFullLogs())
        }

        // Receiving extended information about the CI build details
        then: 'Checking the CiBuildDetail values'
        verifyAll { // soft assert. Will show all the failed assertions
            ciBuildDetailInfo['associationType'] == 'attached'
            ciBuildDetailInfo['result'] == CIbuildResult
            cbd['buildTriggerSource'] == "CI"
        }

        then: "Verify artifact info which is sent after PBA 'AssociateBuildToRelease'"
        verifyAll {
            artifact != null
            artifact.getDisplayPath() == 'artifact.log'
            artifact.getFileName() == 'artifact.log'
            artifact.getHref() == "artifact.log"
            artifact.getSize() != null

            artifact.getArtifactName() == "test:AssociateBuildToRelease"
            artifact.getRepositoryName() == "default"
            artifact.getRepositoryType() == "Flow Artifact Repository"
            artifact.getArtifactVersion() != null
            artifact.getArtifactVersionName() != null
            artifact.getUrl() != null
        }

        where:
        caseId | CDReleaseOutcome | CIbuildResult
        '1'    | 'success'        | "SUCCESS"
        '2'    | 'success'        | "UNSTABLE"
        '3'    | 'success'        | "FAILURE"
    }

    @Unroll
    def "#caseId. AssociateBuildToRelease - Negative"() {
        given: 'Parameters for the pipeline'

        def ciPipelineParameters = [
                flowConfigName : CI_CONFIG_NAME,
                flowProjectName: projectName,
                flowReleaseName: releaseName,
                flowRuntimeId  : flowRuntimeId,
                runOnly        : testPbaName
        ]

        when: 'Run pipeline and collect run properties'
        JenkinsBuildJob ciJob = jjr.run(PIPELINE_NAME, ciPipelineParameters)

        then: 'Collecting the result objects'
        assert ciJob.getOutcome() == 'success': "Pipeline on Jenkins was started."
        assert !ciJob.isSuccess(): "Pipeline on Jenkins is finished with error."

        then: "checking the logs"
        assert ciJob.consoleLogContains(logMessage)

        where:
        caseId       | projectName        | releaseName        | flowRuntimeId          | logMessage
        'C388045'    | projects.empty     | releases.correct   | flowRuntimeIds.empty   | logMessages.noSuchProject
        'C388046'    | projects.correct   | releases.empty     | flowRuntimeIds.empty   | logMessages.releaseNotFound
        'C500312.1'  | projects.incorrect | releases.correct   | flowRuntimeIds.empty   | logMessages.noSuchProject
        'C388045.xx' | projects.empty     | releases.correct   | flowRuntimeIds.correct | logMessages.noSuchProject
        'C388046.xx' | projects.correct   | releases.empty     | flowRuntimeIds.correct | logMessages.releaseNotFound
        'C500312.2'  | projects.correct   | releases.incorrect | flowRuntimeIds.empty   | logMessages.noSuchRelease
    }


    static void checkArtifactDetails(ArtifactDetails ar, boolean isFull) {
        String expectedArtifactName = "gradle-test-build-4.9.jar"

        // check minimal
        assert ar != null
        assert ar.getDisplayPath() == expectedArtifactName
        assert ar.getFileName() == expectedArtifactName
        assert ar.getHref() == "build/libs/$expectedArtifactName"
        assert ar.getSize() != null

        if (isFull) {
            // check extended
            assert ar.getArtifactName() == "pv:PBATests"
            assert ar.getRepositoryName() == "default"
            assert ar.getRepositoryType() == "Flow Artifact Repository"
            assert ar.getArtifactVersion() != null
            assert ar.getArtifactVersionName() != null
            assert ar.getUrl() != null
        } else {
            assert ar.getArtifactName() == expectedArtifactName
        }
    }

    static void checkTestResults(TestResults tr) {
        assert tr.getTotalCount() == 3
        assert tr.getPassPercentage() == 100
        assert tr.getFailPercentage() == 0
    }


}
