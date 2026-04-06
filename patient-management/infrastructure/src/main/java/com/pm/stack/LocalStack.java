package com.pm.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalStack extends Stack {

    private Vpc vpc;
    private Cluster ecsCluster;

    public LocalStack(final App scope, final String id, final StackProps props){
        super(scope, id, props);
        this.vpc = createVpc();
        DatabaseInstance authServiceDb =  createDatabase("AuthServiceDB","auth-service-db");
        DatabaseInstance patientServiceDb = createDatabase("PatientServiceDB","patient-service-db");
        CfnHealthCheck authServiceDBHealthCheck = createHealthCheck(authServiceDb, "authServiceDBHealthCheck");
        CfnHealthCheck patientServiceDBHealthCheck = createHealthCheck(patientServiceDb, "patientServiceDBHealthCheck");
        CfnCluster mskCluster = createMskCluster();
        this.ecsCluster = createEcsCluster();
        FargateService authService = createFargateService("AuthService", "auth-service",
                List.of(4005),
                authServiceDb,
                Map.of("JWT_SECRET", "sW/VHiNWXwiPmYKegWQMvm4T/baNYNzHI1NTttExqTg="));
        authService.getNode().addDependency(authServiceDBHealthCheck);
        authService.getNode().addDependency(authServiceDb);

        FargateService billingService = createFargateService("BillingService", "billing-service",
                List.of(4001, 9001 ),
                null,
                null);

        FargateService analyticsService = createFargateService("AnalyticsService", "analytics-service",
                List.of(4002),
                null,
                null);
        analyticsService.getNode().addDependency(mskCluster);

         FargateService patientService = createFargateService("PatientService", "patient-service",
                List.of(4000),
                patientServiceDb,
                Map.of("BILLING_SERVICE_ADDRESS", "host.docker.internal", "BILLING_SERVICE_GRPC_PORT", "9001"));
        patientService.getNode().addDependency(patientServiceDb);
        patientService.getNode().addDependency(patientServiceDBHealthCheck);
        patientService.getNode().addDependency(billingService);
        patientService.getNode().addDependency(mskCluster);

        createApiGateway();

    }

    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "PatientManagementCluster")
//                .clusterName("PatientManagementEcsCluster")
                .vpc(vpc)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("patient-management.local")
                        .build())
                .build();
    }

    private CfnCluster createMskCluster() {
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("kafka-cluster")
                .kafkaVersion("4.1.x.kraft")
                .numberOfBrokerNodes(2)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.large")
                        .clientSubnets(vpc.getPrivateSubnets().stream().map(ISubnet::getSubnetId).toList())
                        .brokerAzDistribution("DEFAULT")
                        .build())
                .build();
    }

    private CfnHealthCheck createHealthCheck(DatabaseInstance authServiceDb, String authServiceDBHealthCheck) {
        return CfnHealthCheck.Builder.create(this, authServiceDBHealthCheck)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(authServiceDb.getDbInstanceEndpointPort()))
                        .ipAddress(authServiceDb.getDbInstanceEndpointAddress())
                        .requestInterval(30)
                        .failureThreshold(3)
                        .build()
                )
                .build();
    }

    private Vpc createVpc() {
        return Vpc.Builder.create(this, "PatientManagementVpc").vpcName("PatientManagementVpc").maxAzs(2).build();
    }

    private DatabaseInstance createDatabase(String id, String dbName){
        return DatabaseInstance.Builder.create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder().
                                version(PostgresEngineVersion.VER_14)
                                .build()
                    )
                )
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20)
                .databaseName(dbName)
                .credentials(Credentials.fromGeneratedSecret("admin_user"))
                .build();
    }

    private FargateService createFargateService(String id, String imageName, List<Integer> ports, DatabaseInstance db, Map<String, String> environmentVariables) {
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this, id + "TaskDefinition")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();
        ContainerDefinitionOptions.Builder containerDefinitionOptions = ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry(imageName))
                .portMappings(ports.stream().map(port -> PortMapping.builder()
                        .containerPort(port)
                        .hostPort(port)
                        .protocol(Protocol.TCP)
                        .build()).toList())
                .logging(LogDrivers.awsLogs(AwsLogDriverProps.builder()
                                .streamPrefix(imageName)
                                .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                        .logGroupName("/ecs/" + imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                        .build()));


        Map<String, String> envVars = new HashMap<>();
        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4510,localhost.localstack.cloud:4511,localhost.localstack.cloud:4512");
        if(environmentVariables != null){
            envVars.putAll(environmentVariables);
        }
        if(db != null){
            envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s-db".formatted( db.getDbInstanceEndpointAddress(), db.getDbInstanceEndpointPort(),imageName));
            envVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
            envVars.put("SPRING_DATASOURCE_PASSWORD", db.getSecret().secretValueFromJson("password").toString());
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
        }
        containerDefinitionOptions.environment(envVars);

         taskDefinition.addContainer(id + "Container", containerDefinitionOptions.build());

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false)
                .serviceName(imageName)
                .build();
    }

    private void createApiGateway(){
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this,  "ApiGatewayTaskDefinition")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();
        ContainerDefinitionOptions containerDefinitionOptions = ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("api-gateway"))
                .environment(Map.of(
                        "SPRING_PROFILES_ACTIVE", "prod",
                        "AUTH_SERVICE_URL", "http://host.docker.internal:4005"
                ))
                .portMappings(List.of(4004).stream().map(port -> PortMapping.builder()
                        .containerPort(port)
                        .hostPort(port)
                        .protocol(Protocol.TCP)
                        .build()).toList())
                .logging(LogDrivers.awsLogs(AwsLogDriverProps.builder()
                        .streamPrefix("api-gateway")
                        .logGroup(LogGroup.Builder.create(this,  "ApiGatewayLogGroup")
                                .logGroupName("/ecs/api-gateway" )
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .retention(RetentionDays.ONE_DAY)
                                .build())
                        .build()))
                .build();

        taskDefinition.addContainer("ApiGatewayContainer", containerDefinitionOptions);

        ApplicationLoadBalancedFargateService apiGatewayService = ApplicationLoadBalancedFargateService.Builder.create(this, "ApiGatewayService")
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
//                .assignPublicIp(false)
                .serviceName("api-gateway")
                .desiredCount(1)
                .healthCheckGracePeriod(Duration.seconds(60))
                .build();

    }

    public static void main(final String[] args) {
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();

        new LocalStack(app, "localStack", props);
        app.synth();

        System.out.println("Synthesizing started.");


    }
}
