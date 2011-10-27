@GrabResolver(name = 'central', root = 'http://repo1.maven.org/maven2')
@GrabResolver(name = 'javanet', root = 'http://download.java.net/maven/2')
@Grab(group = 'com.amazonaws', module = 'aws-java-sdk', version = '1.1.6')
@Grab(group = 'commons-logging', module = 'commons-logging', version = '1.1.1')
@Grab(group = 'commons-codec', module = 'commons-codec', version = '1.4')
@Grab(group = 'commons-httpclient', module = 'commons-httpclient', version = '3.1')
@Grab(group = 'javax.mail', module = 'mail', version = '1.4.3')
@Grab(group = 'org.codehaus.jackson', module = 'jackson-core-asl', version = '1.4.3')
@Grab(group = 'stax', module = 'stax', version = '1.2.0')
@Grab(group = 'stax', module = 'stax-api', version = '1.0.1')

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.PropertiesCredentials

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.Bucket
import com.amazonaws.services.s3.model.StorageClass
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.ProgressEvent
import com.amazonaws.services.s3.model.ProgressListener
import com.amazonaws.services.s3.transfer.Upload
import com.amazonaws.services.s3.transfer.TransferManager

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient
import com.amazonaws.services.elasticbeanstalk.model.S3Location
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest
import com.amazonaws.services.elasticbeanstalk.model.ApplicationVersionDescription
import org.apache.commons.logging.LogFactory

void deploy(applicationName, versionLabel, environmentName, fileToUpload) {
//deploy(deployBeanstalk: 'Deploy to AWS Beanstalk') {
    def credentialsFile = new File("credentials.properties")
    if (!credentialsFile.exists()) {
        log(applicationName, "File '${credentialsFile}' does not exist, you have to have one in the same")
        log(applicationName, "directory from where you're executing this script. It should have two keys")
        log(applicationName, "with names 'accessKey' and 'secretKey' with respective content.")
        System.exit(1)
    } else {
        log(applicationName, "Loading 'credentials.propeties' file")
    }


    def credentials = new PropertiesCredentials(credentialsFile)
    log(applicationName, "Loaded AWS credentials")


    AmazonS3 s3 = new AmazonS3Client(credentials)
    AWSElasticBeanstalk elasticBeanstalk = new AWSElasticBeanstalkClient(credentials)

    // Delete existing application
//    if (applicationVersionAlreadyExists(elasticBeanstalk)) {
//        println "Delete existing application version"
//        def deleteRequest = new DeleteApplicationVersionRequest(applicationName: applicationName,
//                versionLabel: versionLabel, deleteSourceBundle: true)
//        elasticBeanstalk.deleteApplicationVersion(deleteRequest)
//    }

    // Upload a WAR file to Amazon S3
    println "Uploading application to Amazon S3"
    def warFile = fileToUpload
    String bucketName = elasticBeanstalk.createStorageLocation().getS3Bucket()
    String key = URLEncoder.encode(warFile.name, 'UTF-8')
    def s3Result = s3.putObject(bucketName, key, warFile)
    println "Uploaded application $s3Result.versionId"

    // Register a new application version
    println "Create application version with uploaded application"
    def createApplicationRequest = new CreateApplicationVersionRequest(
            applicationName: applicationName, versionLabel: versionLabel,
            description: "Uploaded automatically",
            autoCreateApplication: true, sourceBundle: new S3Location(bucketName, key)
    )
    def createApplicationVersionResult = elasticBeanstalk.createApplicationVersion(createApplicationRequest)
    println "Registered application version $createApplicationVersionResult"

    println "Update environment with uploaded application version"
    def updateEnviromentRequest = new UpdateEnvironmentRequest(environmentName: environmentName, versionLabel: versionLabel)
    def updateEnviromentResult = elasticBeanstalk.updateEnvironment(updateEnviromentRequest)
    println "Updated environment $updateEnviromentResult"
}

void log(message) {
    println "[${new Date().format('yyyy/MM/dd HH:mm:ss')}] ${message}"
}

void log(returnLine = false, appName, message) {

    def msg = "[${new Date().format('yyyy/MM/dd HH:mm:ss')}] [${appName}] ${message}"
    if (returnLine)
        print "\r${msg}"
    else
        println msg
}

//disable output messages for aws sdk
def logAttribute = "org.apache.commons.logging.Log"
def logValue = "org.apache.commons.logging.impl.NoOpLog"
LogFactory.getFactory().setAttribute(logAttribute, logValue)

//script
if (args.size() != 4) {
    log("Usage: groovy beanstalkUpload.groovy <path_to_war> <application_name> <environmentName> <application_version_label>")
    System.exit(1)
}

def fileToUpload = new File(args[0])
def appName = args[1].toLowerCase()
def environmentName = args[2]
def appVersion = args[3]

if (!fileToUpload.exists()) {
    log("File '${fileToUpload}' does not exist")
    System.exit(1)
}

deploy(appName, appVersion, environmentName, fileToUpload)