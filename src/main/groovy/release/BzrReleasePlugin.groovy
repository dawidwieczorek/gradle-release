package release

import org.gradle.api.GradleException

/**
 * @author elberry
 * @author evgenyg
 * Created: Tue Aug 09 23:26:04 PDT 2011
 */
class BzrReleasePlugin extends BaseScmPlugin {

	private static final String ERROR = 'ERROR'
	private static final String DELIM = '\n  * '

	@Override
	void init() {

		boolean hasXmlPlugin = exec('bzr', 'plugins').readLines().any { it.startsWith('xmloutput') }

		if (!hasXmlPlugin) {
			throw new GradleException('The required xmloutput plugin is not installed in Bazaar, please install it.')
		}

		setConvention('BzrReleasePlugin', new BzrReleasePluginConvention())
	}


	void checkCommitNeeded() {
		String out = exec('bzr', 'xmlstatus')
		def xml = new XmlSlurper().parseText(out)
		def added = xml.added?.size() ?: 0
		def modified = xml.modified?.size() ?: 0
		def removed = xml.removed?.size() ?: 0
		def unknown = xml.unknown?.size() ?: 0

		def c = { String name ->
			["${ capitalize(name)}:",
					xml."$name".file.collect { it.text().trim() },
					xml."$name".directory.collect { it.text().trim() }].
					flatten().
					join(DELIM) + '\n'
		}

		if (unknown) {
			warnOrThrow(releaseConvention().failOnUnversionedFiles, "You have un-versioned files:\n${c('unknown')}")
		} else if (added || modified || removed) {
			def message = 'You have un-committed files:\n' +
					(added ? c('added') : '') +
					(modified ? c('modified') : '') +
					(removed ? c('removed') : '')
			warnOrThrow(releaseConvention().failOnCommitNeeded, message)
		}
	}


	void checkUpdateNeeded() {
		String out = exec('bzr', 'xmlmissing')
		def xml = new XmlSlurper().parseText(out)
		int extra = ("${xml.extra_revisions?.@size}" ?: 0) as int
		int missing = ("${xml.missing_revisions?.@size}" ?: 0) as int

		//noinspection GroovyUnusedAssignment
		Closure c = {
			int number, String name, String path ->

			["You have $number $name changes${ number == 1 ? '' : 's' }:",
					xml."$path".logs.log.collect {
						int cutPosition = 40
						String message = it.message.text()
						message = message.readLines()[0].substring(0, Math.min(cutPosition, message.size())) +
								(message.size() > cutPosition ? ' ..' : '')
						"[$it.revno]: [$it.timestamp][$it.committer][$message]"
					}].
					flatten().
					join(DELIM)
		}

		if (extra > 0) {
			warnOrThrow(releaseConvention().failOnPublishNeeded, c(extra, 'unpublished', 'extra_revisions'))
		}

		if (missing > 0) {
			warnOrThrow(releaseConvention().failOnUpdateNeeded, c(missing, 'missing', 'missing_revisions'))
		}
	}


	void createReleaseTag() {
		exec(['bzr', 'tag', project.properties.version], 'Error creating tag', ERROR)
	}


	void commit(String message) {
		exec(['bzr', 'ci', '-m', message], 'Error committing new version', ERROR)
		exec(['bzr', 'push', ':parent'], 'Error committing new version', ERROR)
	}

}