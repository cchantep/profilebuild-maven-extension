package mojo;

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

import org.apache.maven.artifact.repository.ArtifactRepository;

import org.apache.maven.repository.RepositorySystem;

import org.apache.maven.artifact.resolver.filter.ArtifactFilter;

import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.annotations.Component;

import org.codehaus.plexus.util.xml.Xpp3Dom;

import org.codehaus.plexus.logging.Logger;

/**
 * Profile build.
 *
 * It attaches dependency artifacts from profile properties. 
 * Gets profile properties matching given a prefix, 
 * then parses those properties. Format used in those properties to 
 * define artifact is groupId:artifactId:version:type:scope 
 * (only scope can be omited). 
 * Each property can define one or more dependencies, artifact strings 
 * being separated by space (' ').
 * Profile extra artifacts are then treated by standard artifact mecanism.
 *
 * @author Cedric Chantepie
 */
@Component(role = AbstractMavenLifecycleParticipant.class, 
           hint = "profiledep")
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
        final ArtifactRepository localRepository = session.getLocalRepository();
        final List<ArtifactRepository> remoteRepositories =
            project.getRemoteArtifactRepositories();

        final String prefix = project.
            getProperties().getProperty("profiledep.prefix");

	log.debug("prefix = " + prefix);

	if (prefix == null ||
	    prefix.trim().length() == 0) {

	    log.error("No property prefix: profiledep.prefix");

	    return;
	} // end of if

        final Plugin packaging = getPackagingPlugin(project);

        if (packaging == null) {
            throw new IllegalArgumentException("Unsupported packaging");
        } // end of if

        // ---

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
            new ProfileUtility(log,
                               project,
                               prefix,
                               this.repoSys,
                               remoteRepositories,
                               localRepository);

        final String classifier = 
            getClassifier(session, util, project, packaging, pkgConfig);

        if (classifier == null) {
            throw new IllegalArgumentException("No packaging classifier for profile build");
            
        } // end of if

        // ---

        log.debug("classifier = " + classifier);

        updateClassifier(packaging, pkgConfig, classifier);

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
    } // end of afterProjectsRead

    // ---

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
    private String getClassifier(final MavenSession session,
                                 final ProfileUtility util,
                                 final MavenProject project,
                                 final Plugin packaging,
                                 final Xpp3Dom pkgConfig) {

        final Xpp3Dom classifier = pkgConfig.getChild("classifier");
        
        if (classifier != null) {
            return classifier.getValue();
        } // end of if

        // ---

        final String userClassifier = session.
            getUserProperties().getProperty("profilebuild.classifier");

        final List<String> profileClassifier = util.
            getProfileProperty("profilebuild.classifier");

        if (profileClassifier.size() > 1) {
            log.warn("Multiple active profile defining property: profilebuild.classifier");
        } // end of if

        return (userClassifier != null)
            ? userClassifier
            : (!profileClassifier.isEmpty())
            ? profileClassifier.get(0) : null;

    } // end of getClassifier

    /**
     * Updates |packaging| |classifier|.
     */
    private void updateClassifier(final Plugin packaging,
                                  final Xpp3Dom pkgConfig,
                                  final String classifier) {

        final Xpp3Dom config = new Xpp3Dom("configuration");
        final Xpp3Dom c = new Xpp3Dom("classifier");

        c.setValue(classifier);

        config.addChild(c);

        packaging.setConfiguration(Xpp3Dom.mergeXpp3Dom(pkgConfig, config));

        Xpp3Dom exc;
        for (final PluginExecution ex : packaging.getExecutions()) {
            exc = null;

            try {
                exc = (Xpp3Dom) ex.getConfiguration();
            } catch (Exception e) {
                log.debug("No execution configuration: " + ex.getId());

                continue;
            } // end of catch
            
            ex.setConfiguration(Xpp3Dom.mergeXpp3Dom(exc, config));
        } // end of if for
    } // end of updateClassifier
} // end of class ProfileBuildLifecycleParticipant
