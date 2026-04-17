package org.apache.maven.lifecycle.internal;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import junit.framework.TestCase;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link DefaultLifecycleTaskSegmentCalculator}.
 *
 * Covers null/empty goal scenarios that previously caused NullPointerException
 * when the session has no top-level project and no goals.
 */
public class DefaultLifecycleTaskSegmentCalculatorTest
    extends TestCase
{
    /**
     * Reproduces the NPE: session has null goals and null top-level project.
     *
     * Before the fix, calling calculateTaskSegments() threw NullPointerException
     * at the line: if ( !StringUtils.isEmpty( rootProject.getDefaultGoal() ) )
     * because rootProject was null.
     */
    public void testCalculateTaskSegmentsWithNullGoalsAndNoTopLevelProject()
        throws Exception
    {
        DefaultLifecycleTaskSegmentCalculator calc = new DefaultLifecycleTaskSegmentCalculator();

        MavenSession session = createSessionWithGoals( null );
        // topLevelProject is null because no projects are set

        List<TaskSegment> result = calc.calculateTaskSegments( session );

        assertNotNull( "Result should not be null", result );
        assertEquals( "Expected empty segment list when no goals and no top-level project", 0, result.size() );
    }

    /**
     * Session has empty goals list and null top-level project.
     * Should also return an empty task segment list without NPE.
     */
    public void testCalculateTaskSegmentsWithEmptyGoalsAndNoTopLevelProject()
        throws Exception
    {
        DefaultLifecycleTaskSegmentCalculator calc = new DefaultLifecycleTaskSegmentCalculator();

        MavenSession session = createSessionWithGoals( Collections.<String>emptyList() );

        List<TaskSegment> result = calc.calculateTaskSegments( session );

        assertNotNull( "Result should not be null", result );
        assertEquals( "Expected empty segment list when goals are empty and no top-level project", 0, result.size() );
    }

    /**
     * Session has null goals but a top-level project with a default goal.
     * The default goal should be used and a non-empty segment list returned.
     */
    public void testCalculateTaskSegmentsUsesDefaultGoalFromTopLevelProject()
        throws Exception
    {
        DefaultLifecycleTaskSegmentCalculator calc = new DefaultLifecycleTaskSegmentCalculator();

        MavenProject project = new MavenProject();
        project.setDefaultGoal( "install" );

        MavenSession session = createSessionWithGoals( null );
        session.setProjects( Collections.singletonList( project ) );

        List<TaskSegment> result = calc.calculateTaskSegments( session );

        assertNotNull( "Result should not be null", result );
        assertEquals( "Expected one task segment from the default goal", 1, result.size() );
    }

    /**
     * Explicit null passed to the overloaded calculateTaskSegments(session, tasks).
     * Before the fix this would NPE at new ArrayList<>(tasks.size()).
     */
    public void testCalculateTaskSegmentsOverloadWithNullTaskList()
        throws Exception
    {
        DefaultLifecycleTaskSegmentCalculator calc = new DefaultLifecycleTaskSegmentCalculator();

        MavenSession session = createSessionWithGoals( Arrays.asList( "install" ) );

        List<TaskSegment> result = calc.calculateTaskSegments( session, null );

        assertNotNull( "Result should not be null", result );
        assertEquals( "Expected empty segment list for null task list", 0, result.size() );
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private MavenSession createSessionWithGoals( List<String> goals )
    {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setGoals( goals );
        return new MavenSession( null, null, request, new DefaultMavenExecutionResult() );
    }
}
