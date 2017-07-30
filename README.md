# Mini

A small library to make coremods less excruciating to write.

## Depending on Mini

Mini is designed to be shaded. If you don't shade it, you'll probably break
something really bad.

Merge the following with your build.gradle to use Mini:

```gradle
plugins {
	id 'com.github.johnrengelman.shadow' version '1.2.3'
}

repositories {
	maven {
		url = 'https://repo.elytradev.com'
	}
}

jar {
	classifier = 'slim'
}

shadowJar {
	classifier = ''
	relocate 'com.elytradev.mini', '<me.mymod>.repackage.com.elytradev.mini'
	configurations = [project.configurations.shadow]
}

reobf {
	shadowJar { mappingType = 'SEARGE' }
}

tasks.build.dependsOn reobfShadowJar

artifacts {
	archives shadowJar
}

dependencies {
	compile 'com.elytradev:mini:0.1-SNAPSHOT'
	shadow 'com.elytradev:mini:0.1-SNAPSHOT'
}
```

Make sure to add `com.elytradev.mini` to your transformer exclusions to prevent
strangeness in a development environment.

Of course, any other method of shading will work too. The Gradle Shadow plugin
is what we recommend, though.

Alternatively, you can use the [Elytra Project Skeleton](https://github.com/elytra/skel),
which is designed for Elytra mods, but should work for any mod project.

(As of the time of writing, the skeleton doesn't support coremods. Once it does,
adding Mini to your mod will be extremely easy.)