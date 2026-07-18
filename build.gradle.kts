plugins {
    id("java-library")
    id("maven-publish")
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.1"
}

group = "com.fushu.mmce"
version = "0.1.0"

java {
    withSourcesJar()
}

minecraft {
    mcVersion.set("1.12.2")
    username.set("Developer")
    injectedTags.put("VERSION", project.version.toString())
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven")
    maven("https://maven.cleanroommc.com")
    maven("https://maven.blamejared.com/")
    maven("https://nexus.gtnewhorizons.com/repository/public/")
    maven("https://cfa2.cursemaven.com")
    maven("https://cursemaven.com")
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
    mavenLocal()
}

dependencies {
    patchedMinecraft("me.eigenraven.java8unsupported:java-8-unsupported-shim:1.0.0")

    val modularMachineryCurse = (project.findProperty("modularMachineryCurse") as String?)
        ?: "curse.maven:modular-machinery-community-edition-817377:7372953"
    val mmceGuiExtMaven = (project.findProperty("mmceGuiExtMaven") as String?)
        ?: "com.fushu.mmce:MMCEGE:1.3.0"
    val mmcegeLocalJarPath = (project.findProperty("mmcegeLocalJar") as String?)?.trim()
    val mmcegeLocalJar = if (mmcegeLocalJarPath.isNullOrEmpty()) null else file(mmcegeLocalJarPath)

    implementation(rfg.deobf(modularMachineryCurse))

    if (mmcegeLocalJar != null && mmcegeLocalJar.exists()) {
        compileOnly(files(mmcegeLocalJar))
        runtimeOnly(files(mmcegeLocalJar))
        testCompileOnly(files(mmcegeLocalJar))
        testRuntimeOnly(files(mmcegeLocalJar))
    } else {
        compileOnly(mmceGuiExtMaven)
        runtimeOnly(mmceGuiExtMaven)
        testCompileOnly(mmceGuiExtMaven)
        testRuntimeOnly(mmceGuiExtMaven)
    }

    compileOnly(rfg.deobf("curse.maven:Mekanism-268560:2835175"))
    runtimeOnly(rfg.deobf("curse.maven:Mekanism-268560:2835175"))
    testCompileOnly(rfg.deobf("curse.maven:Mekanism-268560:2835175"))
    testRuntimeOnly(rfg.deobf("curse.maven:Mekanism-268560:2835175"))
    compileOnly("software.bernie.geckolib:geckolib-forge-1.12.2:3.0.31")
    testCompileOnly("software.bernie.geckolib:geckolib-forge-1.12.2:3.0.31")
    testRuntimeOnly("software.bernie.geckolib:geckolib-forge-1.12.2:3.0.31")
    runtimeOnly("CraftTweaker2:CraftTweaker2-MC1120-Main:1.12-4.1.20.715")
    testRuntimeOnly("CraftTweaker2:CraftTweaker2-MC1120-Main:1.12-4.1.20.715")
    testImplementation("junit:junit:4.13.2")
}

tasks.processResources.configure {
    inputs.property("version", project.version)
    inputs.property("mcversion", minecraft.mcVersion.get())
    filesMatching("mcmod.info") {
        expand(
            mapOf(
                "version" to project.version,
                "mcversion" to minecraft.mcVersion.get()
            )
        )
    }
}

tasks.compileJava.configure {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
}

tasks.compileTestJava.configure {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
}

tasks.withType<JavaExec>().configureEach {
    if (name == "runClient" || name == "runServer") {
        maxHeapSize = "1536m"
        jvmArgs("-XX:TieredStopAtLevel=1", "-XX:CICompilerCount=2")
        if ((project.findProperty("mmceOneBlockDevValidation") as String?)?.toBoolean() == true) {
            systemProperty("mmceoneblock.devValidation", "true")
        }
        if ((project.findProperty("mmceOneBlockClientGuiValidation") as String?)?.toBoolean() == true) {
            systemProperty("mmceoneblock.clientGuiValidation", "true")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
