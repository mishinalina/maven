package org.apache.maven.artifact.versioning;

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

/**
 * Tests {@link Restriction} bound handling, equality and string rendering.
 */
public class RestrictionTest
    extends TestCase
{
    private static ArtifactVersion version( String version )
    {
        return new DefaultArtifactVersion( version );
    }

    public void testEverythingContainsAnyVersion()
    {
        assertTrue( "check EVERYTHING contains 1.0", Restriction.EVERYTHING.containsVersion( version( "1.0" ) ) );
        assertTrue( "check EVERYTHING contains snapshot",
                    Restriction.EVERYTHING.containsVersion( version( "5.2-SNAPSHOT" ) ) );
        assertNull( "check EVERYTHING has no lower bound", Restriction.EVERYTHING.getLowerBound() );
        assertNull( "check EVERYTHING has no upper bound", Restriction.EVERYTHING.getUpperBound() );
    }

    public void testInclusiveBounds()
    {
        Restriction restriction = new Restriction( version( "1.0" ), true, version( "2.0" ), true );

        assertTrue( "check lower bound included", restriction.containsVersion( version( "1.0" ) ) );
        assertTrue( "check interior version included", restriction.containsVersion( version( "1.5" ) ) );
        assertTrue( "check upper bound included", restriction.containsVersion( version( "2.0" ) ) );
        assertFalse( "check version below range excluded", restriction.containsVersion( version( "0.9" ) ) );
        assertFalse( "check version above range excluded", restriction.containsVersion( version( "2.0.1" ) ) );
    }

    public void testExclusiveBounds()
    {
        Restriction restriction = new Restriction( version( "1.0" ), false, version( "2.0" ), false );

        assertFalse( "check lower bound excluded", restriction.containsVersion( version( "1.0" ) ) );
        assertTrue( "check interior version included", restriction.containsVersion( version( "1.5" ) ) );
        assertFalse( "check upper bound excluded", restriction.containsVersion( version( "2.0" ) ) );
    }

    public void testLowerBoundOnly()
    {
        Restriction restriction = new Restriction( version( "1.0" ), true, null, false );

        assertTrue( "check lower bound included", restriction.containsVersion( version( "1.0" ) ) );
        assertTrue( "check much larger version included", restriction.containsVersion( version( "100.0" ) ) );
        assertFalse( "check version below range excluded", restriction.containsVersion( version( "0.1" ) ) );
    }

    public void testUpperBoundOnly()
    {
        Restriction restriction = new Restriction( null, false, version( "2.0" ), true );

        assertTrue( "check upper bound included", restriction.containsVersion( version( "2.0" ) ) );
        assertTrue( "check much smaller version included", restriction.containsVersion( version( "0.1" ) ) );
        assertFalse( "check version above range excluded", restriction.containsVersion( version( "2.1" ) ) );
    }

    public void testEqualsAndHashCode()
    {
        Restriction restriction = new Restriction( version( "1.0" ), true, version( "2.0" ), false );
        Restriction same = new Restriction( version( "1.0" ), true, version( "2.0" ), false );

        assertEquals( "check reflexive equality", restriction, restriction );
        assertEquals( "check equal restrictions", restriction, same );
        assertEquals( "check equal restrictions in reverse", same, restriction );
        assertEquals( "check equal hash codes", restriction.hashCode(), same.hashCode() );

        Restriction everything = new Restriction( null, false, null, false );
        assertEquals( "check unbounded restrictions equal", Restriction.EVERYTHING, everything );
        assertEquals( "check unbounded hash codes equal", Restriction.EVERYTHING.hashCode(), everything.hashCode() );
    }

    public void testNotEquals()
    {
        Restriction restriction = new Restriction( version( "1.0" ), true, version( "2.0" ), false );

        assertFalse( "check not equal to null", restriction.equals( null ) );
        assertFalse( "check not equal to other type", restriction.equals( "[1.0,2.0)" ) );
        assertFalse( "check different lower bound",
                     restriction.equals( new Restriction( version( "1.1" ), true, version( "2.0" ), false ) ) );
        assertFalse( "check different lower bound inclusiveness",
                     restriction.equals( new Restriction( version( "1.0" ), false, version( "2.0" ), false ) ) );
        assertFalse( "check different upper bound",
                     restriction.equals( new Restriction( version( "1.0" ), true, version( "2.1" ), false ) ) );
        assertFalse( "check different upper bound inclusiveness",
                     restriction.equals( new Restriction( version( "1.0" ), true, version( "2.0" ), true ) ) );
        assertFalse( "check null lower bound vs non-null",
                     restriction.equals( new Restriction( null, true, version( "2.0" ), false ) ) );
        assertFalse( "check null upper bound vs non-null",
                     restriction.equals( new Restriction( version( "1.0" ), true, null, false ) ) );
    }

    public void testToString()
    {
        assertEquals( "check inclusive range", "[1.0,2.0]",
                      new Restriction( version( "1.0" ), true, version( "2.0" ), true ).toString() );
        assertEquals( "check exclusive range", "(1.0,2.0)",
                      new Restriction( version( "1.0" ), false, version( "2.0" ), false ).toString() );
        assertEquals( "check mixed range", "[1.0,2.0)",
                      new Restriction( version( "1.0" ), true, version( "2.0" ), false ).toString() );
        assertEquals( "check unbounded range", "(,)", Restriction.EVERYTHING.toString() );
        assertEquals( "check lower bound only", "[1.0,)",
                      new Restriction( version( "1.0" ), true, null, false ).toString() );
        assertEquals( "check upper bound only", "(,2.0]",
                      new Restriction( null, false, version( "2.0" ), true ).toString() );
    }
}
