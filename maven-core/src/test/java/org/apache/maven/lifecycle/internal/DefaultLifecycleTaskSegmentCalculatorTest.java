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

public class DefaultLifecycleTaskSegmentCalculatorTest
    extends TestCase
{
    public void testCalculateTaskSegmentsWithNullGoalsAndNoProject()
        throws Exception
    {
        DefaultLifecycleTaskSegmentCalculator calculator = new DefaultLifecycleTaskSegmentCalculator();
        MavenSession session = newSessionWithGoals( null );

        List<TaskSegment> segments = calculator.calculateTaskSegments( session );

        assertNotNull( segments );
        assertTrue( segments.isEmpty() );
    }

    public void testCalculateTaskSegmentsWithEmptyGoalsAndNoProject()
        throws Exception
    {
        DefaultLifecycleTaskSegmentCalculator calculator = new DefaultLifecycleTaskSegmentCalculator();
        MavenSession session = newSessionWithGoals( Collections.<String>emptyList() );

        List<TaskSegment> segments = calculator.calculateTaskSegments( session );

        assertNotNull( segments );
        assertTrue( segments.isEmpty() );
    }

    public void testCalculateTaskSegmentsUsesDefaultGoalFromTopLevelProject()
        throws Exception
    {
        DefaultLifecycleTaskSegmentCalculator calculator = new DefaultLifecycleTaskSegmentCalculator();
        MavenSession session = newSessionWithGoals( null );
        MavenProject project = new MavenProject();
        project.setDefaultGoal( "clean install" );
        session.setProjects( Collections.singletonList( project ) );

        List<TaskSegment> segments = calculator.calculateTaskSegments( session );

        assertEquals( 1, segments.size() );
        assertFalse( segments.get( 0 ).isAggregating() );
        assertEquals( 2, segments.get( 0 ).getTasks().size() );
        assertEquals( "clean", segments.get( 0 ).getTasks().get( 0 ).toString() );
        assertEquals( "install", segments.get( 0 ).getTasks().get( 1 ).toString() );
    }

    public void testCalculateTaskSegmentsWithExplicitNullTaskList()
        throws Exception
    {
        DefaultLifecycleTaskSegmentCalculator calculator = new DefaultLifecycleTaskSegmentCalculator();
        MavenSession session = newSessionWithGoals( Arrays.asList( "clean" ) );

        List<TaskSegment> segments = calculator.calculateTaskSegments( session, (List<String>) null );

        assertNotNull( segments );
        assertTrue( segments.isEmpty() );
    }

    private static MavenSession newSessionWithGoals( List<String> goals )
    {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setGoals( goals );
        return new MavenSession( null, null, request, new DefaultMavenExecutionResult() );
    }
}
