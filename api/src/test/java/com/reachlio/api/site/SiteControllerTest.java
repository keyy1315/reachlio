package com.reachlio.api.site;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SiteControllerTest {

	@Test
	void domainOf() {
		assertEquals("example.com", SiteController.domainOf("sc-domain:example.com"));
		assertEquals("example.com", SiteController.domainOf("https://WWW.Example.com/"));
		assertEquals("example.com", SiteController.domainOf("sc-domain:www.example.com"));
		assertEquals("blog.example.com", SiteController.domainOf("http://blog.example.com/path/"));
	}
}
