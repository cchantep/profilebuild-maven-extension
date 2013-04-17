# Maven Profile Build Extension

This extension allows to build project according profile dependencies.

## Usage

To integration in your POM:

```xml
<project>
  ...
  <properties>
    <profiledep.prefix>my.deps.</profiledep.prefix>
    ...
  </properties>

  <build>
    <extensions>
      <extension>
	<groupId>cchantep</groupId>
	<artifactId>profiledep-maven-plugin</artifactId>
	<version>1.0</version>
      </extension>

      ...
    </extensions>
    ...
  </build>
</project>
```

For previous sample POM, profile properties should be prefixed by `my.deps.`

So in Maven profile (`.m2/settings.xml`), you can defined properties in following way:

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

These two kinds of profile properties define space-separated values, profile artifacts for `my.deps.` prefixed one (here `my.deps.xxx`), EAR module specifications for for `my.mods.` one (here `my.mods.xxx`).

For EAR module injection, `moduleType` part of module specification is the type of included module, like `ejb`, `java` or `web`. The `entryName` is used to customized the module filename. When module is `web` one, specification is ended by context path (here `/`).

## Classifier

As using this extension a Maven project can produce multiple distinct artifact, it's required to have a defined `classifier`.

This extension will enforce failure on missing classifier.

If you don't want to define classifier inside your POM, you can specify it using (with descending priority):

1. User property: `mvn -Dprofilebuild.classifier=myclassifier ...`
2. In profile properties (e.g. in `~/.m2/settings.xml`). Take care that only one of active profile is definied the `profilebuild.classifier` property.

## Documentation

More documentation can be found [here](http://cchantep.github.io/maven-profiledep-plugin/).
