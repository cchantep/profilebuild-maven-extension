package mojo;

import java.util.StringTokenizer;
import java.util.Properties;
import java.util.List;
import java.util.Set;

import org.apache.maven.AbstractMavenLifecycleParticipant;

import org.apache.maven.execution.MavenSession;

import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Plugin;

import org.apache.maven.project.MavenProject;

import org.apache.maven.artifact.Artifact;

import org.apache.maven.repository.RepositorySystem;

import org.apache.maven.artifact.resolver.filter.ArtifactFilter;

import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.annotations.Component;

import org.codehaus.plexus.util.xml.Xpp3Dom;

import org.codehaus.plexus.logging.Logger;

/**
 * Profile build.
 *
 * @author Cedric Chantepie
 */
@Component(role = AbstractMavenLifecycleParticipant.class, 
           hint = "profilebuild")
public class ProfileBuildLifecycleParticipant 
    extends AbstractMavenLifecycleParticipant implements ArtifactFilter {

    // --- Properties ---

    /**
     * Logger
     */
    @Requirement
    private Logger log;

    /**
     * Used to look up Artifacts in the remote repository.
     */
    @Requirement
    private RepositorySystem repoSys;

    // ---

    /**
     * Returns true if |artifact| will be included, or false if not.
     */
    public boolean include(final Artifact artifact) {
        return !"provided".equals(artifact.getScope());
    } // end of include

    /**
     * {@inheritDoc}
     */
    public void afterProjectsRead(final MavenSession session) {
	log.info("Attaching profile artifact");

        // Init
        final MavenProject project = session.getCurrentProject();
        final Plugin packaging = getPackagingPlugin(project);

        if (packaging == null) {
            log.warn("Skip unsupported packaging: " + project);
            return;
        } // end of if

        final String prefix = project.
            getProperties().getProperty("profilebuild.prefix");

	log.debug("prefix = " + prefix);

	if (prefix == null || prefix.trim().length() == 0) {
	    log.warn("No property prefix: profilebuild.prefix");
	} else {
            processDependencies(session, packaging, prefix);
        } // end of else

        if (!"maven-ear-plugin".equals(packaging.getArtifactId())) {
            return;
        } // end of if

        // ---

        final String earPrefix = project.
            getProperties().getProperty("profilebuild.earPrefix");

        log.debug("EAR prefix = " + earPrefix);

	if (earPrefix == null || earPrefix.trim().length() == 0) {
	    log.warn("No property EAR prefix: profilebuild.earPrefix");
	} else {
            processEar(session, packaging, earPrefix);
        } // end of else
    } // end of afterProjectsRead

    /**
     * Processes dependencies defined in profiles.
     *
     * @param session Maven session
     * @param packaging Packaging plugin
     * @param prefix Dependencies prefix
     */
    private void processDependencies(final MavenSession session,
                                     final Plugin packaging,
                                     final String prefix) {

        final MavenProject project = session.getCurrentProject();

	Xpp3Dom c = null;

	try {
	    c = (Xpp3Dom) packaging.getConfiguration();
	} catch (Exception e) {
	    log.error("Unsupported packaging configuration");
	    log.debug("configuration = " + c);

            throw new IllegalArgumentException("No packaging configuration");
	} // end of if

        final Xpp3Dom pkgConfig = c;

        log.debug("packaging config = " + pkgConfig);

        // ---

        final ProfileUtility util = 
            new ProfileUtility(this.log,
                               project,
                               prefix,
                               this.repoSys);

        final String classifier = getClassifier(pkgConfig, project);

        if (classifier == null) {
            throw new IllegalArgumentException("No packaging classifier for profile build");
            
        } // end of if

        // ---

        final Set<Artifact> profileArtifacts = util.getProfileArtifacts(this);

	log.debug("profile artifact(s) = " + profileArtifacts);

        // Add as dependencies
        final List<Dependency> dependencies = project.getDependencies();

        for (final Artifact artifact : profileArtifacts) {
            final Dependency dep = new Dependency();

            dep.setGroupId(artifact.getGroupId());
            dep.setArtifactId(artifact.getArtifactId());
            dep.setVersion(artifact.getVersion());
            dep.setClassifier(artifact.getClassifier());
            dep.setType(artifact.getType());
            dep.setScope(artifact.getScope());

            dependencies.add(dep);
        } // end of for

        project.setDependencies(dependencies);
    } // end of processDependencies

    /**
     * Processes EAR, configuring modules defined in profiles.
     *
     * @param session Maven session
     * @param earPlugin EAR plugin
     * @param prefix EAR prefix
     */
    private void processEar(final MavenSession session,
                            final Plugin earPlugin,
                            final String prefix) {

        final MavenProject project = session.getCurrentProject();
	final List<Profile> profiles = project.getActiveProfiles();

	if (profiles == null || profiles.isEmpty()) {
	    log.warn("No profile");
	    log.debug("profiles = " + profiles);

	    return;
	} // end of if

	// ---
        
        final Xpp3Dom modules = new Xpp3Dom("modules");

	Properties props;
	String value;
	StringTokenizer vtok;
        Xpp3Dom module;
	for (final Profile profile : profiles) {
            props = profile.getProperties();

            if (props == null) {
                log.debug("No profile properties: " + profile.getId());

                continue;
            } // end of if

	    // ---

	    log.debug("properties = " + props);
	    
	    for (final String key : props.stringPropertyNames()) {
		log.debug("property key = " + key);
                
		if (!key.startsWith(prefix)) {
		    continue;
		} // end of if
                
		// ---
                
		value = props.getProperty(key);
                
		log.debug("Find matching property: " + key + " = " + value);

		vtok = new StringTokenizer(value, " ");

		while (vtok.hasMoreTokens()) {
                    module = createEarModule(vtok.nextToken());

                    log.debug("module configuration = " + module);

                    modules.addChild(module);
                } // end of while
            } // end of for
        } // end of for

        log.debug("profile EAR modules = " + modules);

	try {
	    final Xpp3Dom c = (Xpp3Dom) earPlugin.getConfiguration();

            earPlugin.setConfiguration(mergeEarModules(c, modules));
	} catch (Exception e) {
	    log.warn("No EAR plugin configuration");
	} // end of if

        for (final PluginExecution ex : earPlugin.getExecutions()) {
            try {
                ex.setConfiguration(mergeEarModules((Xpp3Dom) ex.getConfiguration(), modules));

            } catch (Exception e) {
                log.debug("No execution configuration: " + ex.getId());
            } // end of catch
        } // end of if for
    } // end of processEar

    /**
     * Merges EAR |modules| definitions in existing |configuration|.
     */
    private Xpp3Dom mergeEarModules(final Xpp3Dom configuration,
                                    final Xpp3Dom modules) {

        final Xpp3Dom m = configuration.getChild("modules");

        if (m == null) {
            configuration.addChild(modules);

            return configuration;
        } // end of if

        // ---

        for (final Xpp3Dom module : modules.getChildren()) {
            m.addChild(module);
        } // end of for

        return configuration;
    } // end of mergeEarModules

    /**
     * Creates EAR module definition from string |specification|.
     */
    private Xpp3Dom createEarModule(final String specification) {
        final StringTokenizer tok = new StringTokenizer(specification, ":");

        final int pc = tok.countTokens();

        if (pc < 4) {
            throw new IllegalArgumentException("Invalid EAR module specification: Invalid token count (" + pc + "): " + specification);

        } // end of if

        // ---

        final String groupId = tok.nextToken();
        final String artifactId = tok.nextToken();
        final String type = tok.nextToken();
        final String uri = tok.nextToken();

        log.debug("group id = " + groupId + 
                  ", artifact id = " + artifactId +
                  ", module type = " + type +
                  ", module uri = " + uri);

        final Xpp3Dom module = new Xpp3Dom(type + "Module");

        // Append groupId
        final Xpp3Dom gid = new Xpp3Dom("groupId");

        gid.setValue(groupId);

        module.addChild(gid);

        // Append artifactId
        final Xpp3Dom aid = new Xpp3Dom("artifactId");

        aid.setValue(artifactId);

        module.addChild(aid);

        // Append URI
        final Xpp3Dom u = new Xpp3Dom("uri");

        u.setValue(uri);

        module.addChild(u);

        if ("web".equals(type) && tok.hasMoreTokens()) {
            final Xpp3Dom cr = new Xpp3Dom("contextRoot");

            cr.setValue(tok.nextToken());

            module.addChild(cr);
        } // end of if

        return module;
    } // end of createEarModule

    /**
     * Returns packaging plugin.
     *
     * @param project Current projet
     */
    private Plugin getPackagingPlugin(final MavenProject project) {
	final List<Plugin> plugins = project.getBuildPlugins();
        final String packaging = project.getPackaging();
        final String pluginId = "maven-" + packaging + "-plugin";

        log.debug("plugin id = " + pluginId);

	for (final Plugin p : plugins) {
	    log.debug("plugin = " + p);

            if (pluginId.equals(p.getArtifactId())) {
                return p;
	    } // end of if
	} // end of for

        return null; // Not found
    } // end of getPackagingPlugin

    /**
     * Returns classifier.
     */
    private String getClassifier(final Xpp3Dom pkgConfig,
                                 final MavenProject project) {

        final Xpp3Dom classifier = pkgConfig.getChild("classifier");
        
        if (classifier != null) {
            return classifier.getValue();
        } // end of if

        return project.getProperties().getProperty("profilebuild.classifier");
    } // end of getClassifier
} // end of class ProfileBuildLifecycleParticipant
