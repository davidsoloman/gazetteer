import groovy.text.Template;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import groovy.text.SimpleTemplateEngine;

import me.osm.gazetteer.web.imp.Replacer
import me.osm.gazetteer.web.utils.ReplacersFactory;

class GroovyReplacersFactory implements ReplacersFactory {
	
	def engine = new SimpleTemplateEngine();
	
	public Replacer createReplacer(String pattern, String template) {
		def result = new ReplacerImpl();
		result.patternString = pattern;
		result.pattern = Pattern.compile(pattern);
		result.template = engine.createTemplate(template);
		return result;
	}
}

class ReplacerImpl implements Replacer {
	
	private String patternString;
	private Pattern pattern;
	private Template template;
	
	@Override
	public Collection<String> replace(String hn) {
		
		def rl = [] as Set;
		
		def matcher = pattern.matcher(hn);
		while(matcher.find()) {
			
			def groups = [] as List;
			for(int i = 0; i <= matcher.groupCount(); i++) {
				groups.add(matcher.group(i));
			}
			
			def text = template.make([
				'SU': StringUtils, 
				'groups': groups, 
				'full': hn]);
			
			for(String str in StringUtils.split(text.toString(), "\n")) {
				if(StringUtils.isNotBlank(str)) {
					rl.add(StringUtils.trim(str));
				}
			}
		}
		
		if(rl.size() > 0) {
			return rl;	
		}
		
		return null;
	}
}