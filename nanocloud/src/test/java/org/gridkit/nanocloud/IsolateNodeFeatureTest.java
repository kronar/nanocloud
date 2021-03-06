/**
 * Copyright 2013 Alexey Ragozin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gridkit.nanocloud;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;

import org.gridkit.vicluster.ViProps;
import org.junit.Before;
import org.junit.Test;

public class IsolateNodeFeatureTest extends ViNodeFeatureTest {

	@Before
	public void initCloud() {
		cloud = CloudFactory.createCloud();
		ViProps.at(cloud.node("**")).setIsolateType();
	}
	
	@Test
	@Override
	public void verify_isolated_static_with_void_callable() {
		super.verify_isolated_static_with_callable();
	}

	@Test
	@Override
	public void verify_isolated_static_with_callable() {
		super.verify_isolated_static_with_callable();
	}

	@Test
	@Override
	public void verify_isolated_static_with_runnable() {
		super.verify_isolated_static_with_runnable();
	}

	@Test
	@Override
	public void verify_class_exclusion() {
		super.verify_class_exclusion();
	}

	@Test
	@Override
	public void verify_property_isolation() throws Exception {
		super.verify_property_isolation();
	}
	
	@Test
	@Override
    public void verify_exec_stack_trace_locality() {
        super.verify_exec_stack_trace_locality();
    }

	@Test
    @Override
    public void verify_transparent_proxy_stack_trace() {
        super.verify_transparent_proxy_stack_trace();
    }
	
	@Test
    @Override
    public void verify_transitive_transparent_proxy_stack_trace() {
        super.verify_transitive_transparent_proxy_stack_trace();
    }

	@Test
    @Override
    public void test_dont_inherit_cp() {
        super.test_dont_inherit_cp();
    }

	@Test
	@Override
	public void test_handle_NoDefClassFound(){
		super.test_handle_NoDefClassFound();
	}

	@Test
	@Override
	public void test_handle_NoDefClassFound_on_return(){
	    super.test_handle_NoDefClassFound_on_return();
	}
	
	@Test
    @Override
    public void test_inherit_cp_true() throws IOException, URISyntaxException {
        super.test_inherit_cp_true();
    }

	@Test
    @Override
    public void test_inherit_cp_default_true() {
        super.test_inherit_cp_default_true();
    }

    @Test
	@Override
	public void test_classpath_extention() throws IOException, URISyntaxException {
		super.test_classpath_extention();
	}

    @Test
	@Override
	public void test_classpath_limiting() throws MalformedURLException, URISyntaxException {
		super.test_classpath_limiting();
	}

	@Test
	@Override
	public void test_annonimous_primitive_in_args() {
		super.test_annonimous_primitive_in_args();
	}
}
