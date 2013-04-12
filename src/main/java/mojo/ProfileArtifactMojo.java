package mojo;

import java.util.StringTokenizer;
import java.util.Properties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.lang.reflect.Method;

import org.apache.maven.model.Profile;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.AbstractMojo;

import org.apache.maven.plugin.logging.Log;

import org.apache.maven.project.MavenProject;

import org.apache.maven.artifact.Artifact;

import org.apache.maven.artifact.repository.ArtifactRepository;

import org.apache.maven.repository.RepositorySystem;

import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;

/**
 * Profile artifact injection. 
 * Injects dependencies from profile properties. Gets profile properties matching given prefix, then parses those properties. Format used in those properties to define artifact is groupId:artifactId:version:type:scope (only scope can be omited). 
 * Each property can define one or more dependencies, artifact strings being separated by space (' ').
 * Injected dependencies are then treated by standard artifact mecanism
 * (as static dependencies).
 *
 * @author Cedric Chantepie
 * @executionStrategy once-per-session
 * @goal inject
 * @phase validate
 */
public class ProfileArtifactMojo extends AbstractMojo {
    // --- Properties ---

    /**
     * Current project.
     * @parameter default-value="${project}"
     * @required
     */
    private MavenProject project;

    /**
     * Prefix for profile properties defining dependencies.
     * @parameter
     * @required
     */
    private String prefix = null;

    /**
     * Used to look up Artifacts in the remote repository.
     * 
     * @component role="org.apache.maven.repository.RepositorySystem"
     * @required
     * @readonly
     */
    protected RepositorySystem repoSys;

    /**
     * List of Remote Repositories used by the resolver.
     * 
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @readonly
     * @required
     */
    protected List<ArtifactRepository> remoteRepositories;

    /**
     * Location of the local repository.
     * 
     * @parameter expression="${localRepository}"
     * @readonly
     * @required
     */
    protected ArtifactRepository localRepository;

    // ---

    /**
     * {@inheritDoc}
     */
    public void execute() throws MojoExecutionException {
	final Log log = getLog();

	log.info("Attaching profile artifact");
	log.debug("prefix = " + this.prefix);

	if (this.prefix == null ||
	    this.prefix.trim().length() == 0) {

	    log.error("No property prefix");

	    return;
	} // end of if

	// ---

	final List<Profile> profiles = this.project.getActiveProfiles();

	if (profiles == null || profiles.isEmpty()) {
	    log.warn("No profile");
	    log.info/*debug*/("profiles = " + profiles);

	    return;
	} // end of if

	// ---

	final ArrayList<Artifact> profileArtifacts = new ArrayList<Artifact>();
	Method getProps = null;

	Properties props;
	String value;
	String artSpec;
	StringTokenizer vtok;
	StringTokenizer ptok;
	Artifact a;
	for (final Profile profile : profiles) {
	    if (getProps == null) {
		try {
		    getProps = profile.getClass().getMethod("getProperties");

		} catch (Exception e) {
		    throw new MojoExecutionException("Fails to load properties getter", e);
		} // end of catch
	    } // end of if

	    try {
		props = (Properties) getProps.invoke(profile);
	    } catch (Exception e) {
		throw new MojoExecutionException("Fails to get profile properties", e);
	    } // end of catch

	    // ---

	    log.debug("properties = " + props);
	    
	    for (final Object key : props.keySet()) {
		log.debug("property key = " + key);

		if (!((String) key).startsWith(this.prefix)) {
		    continue;
		} // end of if

		// ---

		value = props.getProperty((String) key);

		log.info/*debug*/("Find matching property: " + 
			  key + " = " + value);
		
		vtok = new StringTokenizer(value, " ");

		while (vtok.hasMoreTokens()) {
		    artSpec = vtok.nextToken();

		    log.debug("artifact specification = " + artSpec);

		    ptok = new StringTokenizer(artSpec, ":");

                    try {
                        final String groupId = ptok.nextToken();
                        final String artifactId = ptok.nextToken();
                        final String version = ptok.nextToken();
                        final String type = ptok.nextToken();

                        a = (!ptok.hasMoreTokens()) 
                            ? this.repoSys.createArtifact(groupId, 
                                                          artifactId,
                                                          version,
                                                          type)
                            : this.repoSys.
                            createArtifact(groupId, 
                                           artifactId,
                                           version,
                                           ptok.nextToken()/*scope*/,
                                           type);
                        
                        final ArtifactResolutionRequest artReq = 
                            new ArtifactResolutionRequest().
                            setArtifact(a).
                            setLocalRepository(this.localRepository).
                            setRemoteRepositories(this.remoteRepositories).
                            setForceUpdate(true). // @todo get from projet
                            setResolveTransitively(true);

                        // @todo __HERE
                        log.info("resolve = " + 
                                 this.repoSys.resolve(artReq).isSuccess());

                    } catch (Exception e) {
                        log.warn("Fails to define artifact: " + artSpec, e);

                        continue;
                    } // end of catch

                    // ---

		    log.info/*debug*/("profile artifact = " + a);

                    profileArtifacts.add(a);
		} // end of while
	    } // end of for
	} // end of while

	log.info/*debug*/("attached profile artifact(s) = " + profileArtifacts);

        /*
	this.project.getDependencies().
	    addAll(dynDeps);

	for (final Artifact d : dynDeps) {
	    log.info("* " + d);
	} // end of for
        */
    } // end of execute
} // end of class ProfileArtifactMojo
