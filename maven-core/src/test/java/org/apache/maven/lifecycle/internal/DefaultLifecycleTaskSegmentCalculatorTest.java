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
import org.apache.maven.lifecycle.internal.stub.ProjectDependencyGraphStub;
import org.apache.maven.project.MavenProject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link DefaultLifecycleTaskSegmentCalculator} covering null/empty goal handling.
 */
public class DefaultLifecycleTaskSegmentCalculatorTest
    extends TestCase
{

    /**
     * Builds a MavenSession whose goals come from the request and whose top-level project
     * is the one passed in (may be {@code null}).
     */
    private MavenSession buildSession( List<String> goals, MavenProject topLevelProject )
    {
        DefaultMavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setGoals( goals );
        MavenSession session = new MavenSession( null, null, request, new DefaultMavenExecutionResult() );
        session.setProjectDependencyGraph( new ProjectDependencyGraphStub() );
        if ( topLevelProject != null )
        {
            session.setProjects( Collections.singletonList( topLevelProject ) );
        }
        return session;
    }

    private DefaultLifecycleTaskSegmentCalculator newCalculator()
    {
        return new DefaultLifecycleTaskSegmentCalculator();
    }

    // -------------------------------------------------------------------------
    // calculateTaskSegments(session, tasks) – two-arg variant
    // -------------------------------------------------------------------------

    /**
     * Passing a {@code null} task list to the two-arg method used to throw
     * NullPointerException at {@code tasks.size()}.
     */
    public void testCalculateTaskSegments_nullTaskList_returnsEmpty()
        throws Exception
    {
        MavenSession session = buildSession( Collections.<String>emptyList(), null );

        List<TaskSegment> segments = newCalculator().calculateTaskSegments( session, null );

        assertNotNull( segments );
        assertTrue( "Expected empty list for null task input", segments.isEmpty() );
    }

    /**
     * Passing an empty task list to the two-arg method should return an empty segment list.
     */
    public void testCalculateTaskSegments_emptyTaskList_returnsEmpty()
        throws Exception
    {
        MavenSession session = buildSession( Collections.<String>emptyList(), null );

        List<TaskSegment> segments =
            newCalculator().calculateTaskSegments( session, Collections.<String>emptyList() );

        assertNotNull( segments );
        assertTrue( "Expected empty list for empty task input", segments.isEmpty() );
    }

    // -------------------------------------------------------------------------
    // calculateTaskSegments(session) – one-arg variant
    // -------------------------------------------------------------------------

    /**
     * When the session has null goals and {@code getTopLevelProject()} returns {@code null},
     * the one-arg method used to throw NullPointerException when trying to access the default
     * goal via {@code rootProject.getDefaultGoal()}.
     */
    public void testCalculateTaskSegments_nullGoals_nullTopLevelProject_returnsEmpty()
        throws Exception
    {
        MavenSession session = buildSession( null, null );

        List<TaskSegment> segments = newCalculator().calculateTaskSegments( session );

        assertNotNull( segments );
        assertTrue( "Expected empty list when goals and top-level project are both absent",
                    segments.isEmpty() );
    }

    /**
     * When the session has an empty goal list and no top-level project, the one-arg method
     * must not throw NullPointerException.
     */
    public void testCalculateTaskSegments_emptyGoals_nullTopLevelProject_returnsEmpty()
        throws Exception
    {
        MavenSession session = buildSession( Collections.<String>emptyList(), null );

        List<TaskSegment> segments = newCalculator().calculateTaskSegments( session );

        assertNotNull( segments );
        assertTrue( "Expected empty list when goals are empty and top-level project is absent",
                    segments.isEmpty() );
    }

    /**
     * When the session has no explicit goals but a top-level project provides a default goal,
     * that default goal should be used and produce a non-empty segment list.
     */
    public void testCalculateTaskSegments_noGoals_withDefaultGoal_usesDefaultGoal()
        throws Exception
    {
        MavenProject project = new MavenProject();
        project.getBuild().setDefaultGoal( "install" );

        MavenSession session = buildSession( Collections.<String>emptyList(), project );

        List<TaskSegment> segments = newCalculator().calculateTaskSegments( session );

        assertNotNull( segments );
        assertFalse( "Expected non-empty segments when top-level project defines a default goal",
                     segments.isEmpty() );
    }

    /**
     * When explicit goals are provided they should be used regardless of any default goal.
     */
    public void testCalculateTaskSegments_explicitGoals_ignoredDefaultGoal()
        throws Exception
    {
        MavenProject project = new MavenProject();
        project.getBuild().setDefaultGoal( "install" );

        MavenSession session = buildSession( Arrays.asList( "clean" ), project );

        List<TaskSegment> segments = newCalculator().calculateTaskSegments( session );

        assertNotNull( segments );
        assertEquals( 1, segments.size() );
        assertEquals( 1, segments.get( 0 ).getTasks().size() );
    }
}
