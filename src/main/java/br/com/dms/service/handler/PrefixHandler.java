package br.com.dms.service.handler;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class PrefixHandler {

	public String handle(String prefix, String suffix) {

		if (StringUtils.isNotBlank(prefix) && !prefix.endsWith(":")) {
			return prefix + StringUtils.capitalize(suffix);
		}
		return prefix + suffix;
	}

}
