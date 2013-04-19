# Maven Profile Build Extension

This extension allows to build project according profile dependencies, 
having a single POM file but using [Maven Settings](http://maven.apache.org/settings.html).

## Usage

This extension can be resolved using following repository:

```xml
<pluginRepository>
  <id>applicius-releases</id>
  <name>Applicius Maven2 Releases Repository</name>
  <url>https://raw.github.com/applicius/mvn-repo/master/releases/</url>
</pluginRepository>
```

### Dependencies

To define extra dependencies in Maven profiles, 
you can update your POM as following:

```xml
<project>
  ...
  <properties>
    <profilebuild.prefix>my.deps.</profilebuild.prefix>
    ...
  </properties>

  <build>
    <extensions>
      <extension>
	<groupId>cchantep</groupId>
	<artifactId>profilebuild-maven-extension</artifactId>
	<version>1.0</version>
      </extension>

      ...
    </extensions>
    ...
  </build>
</project>
```

For previous sample POM, profile properties should be prefixed by `my.deps.`
So in Maven Settings (`.m2/settings.xml`), you should have properties like:

```xml
<settings>
  ...
  <profiles>
    <profile>
      <id>my-activated-profile</id>
      <properties>
        <my.deps.xxx>groupId:artifactId:version:packaging:scope<!-- ... --></my.deps.xxx>
      </properties>
    </profile>
  </profiles>
  ...
</settings>
```

Here only one dependency is defined, `groupId:artifactId:version:packaging:scope`. You can define several dependencies in a single property value by separating them with a space.

Each of these dependencies should match one of following format:
* groupId:artifactId:version:packaging
* groupId:artifactId:version:packaging:scope

Such dependencies can be checked as other ones using `mvn dependency:tree`.

### EAR modules

EAR project case is also taken in account, to be able to define extra 
EAR modules in settings profiles.

Modules are defined in a similar way to profile dependencies supported 
by this extension, so your POM should looks like:

```xml
<project>
  ...
  <properties>
    <profilebuild.earPrefix>my.mods.</profilebuild.earPrefix>
    ...
  </properties>

  <build>
    <extensions>
      <extension>
	<groupId>cchantep</groupId>
	<artifactId>profilebuild-maven-extension</artifactId>
	<version>1.0</version>
      </extension>

      ...
    </extensions>
    ...
  </build>
</project>
```

Profiles modules should then be defined in your Maven Settings like:

```xml
<settings>
  ...
  <profiles>
    <profile>
      <id>my-activated-profile</id>
      <properties>
        <my.mods.xxx>groupId:artifactId:moduleType:moduleUri<!-- ... --></my.mods.xxx>
      </properties>
    </profile>
  </profiles>
  ...
</settings>
```

Here only one module is defined, `groupId:artifactId:moduleType:moduleUri`. You can define several EAR modules in a single property value by separating them with a space.

Each module definition should match one of following formats:
* groupId:artifactId:moduleType:moduleUri
* (only if `moduleType` is `web`) groupId:artifactId:moduleType:moduleUri:contextRoot

The `moduleType` is not packaging, but EAR module type: ejb, web.

Part `moduleUri` is the one used as `<uri>...</uri>` in EAR XML descriptor.

## Classifier

As using this extension a Maven project can produce multiple distinct artifact, it's required to have a defined `classifier`.

This extension will enforce failure on missing classifier.

## Documentation

More documentation can be found [here](http://cchantep.github.io/profilebuild-maven-extension/).

Maven document for previous release (for Maven 2.x) is still only at [there](http://cchantep.github.io/profilebuild-maven-extension/1.0/).
