/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.enterprise.server.content.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.testng.annotations.Test;

import org.rhq.core.domain.content.Architecture;
import org.rhq.core.domain.content.Package;
import org.rhq.core.domain.content.PackageBits;
import org.rhq.core.domain.content.PackageBitsBlob;
import org.rhq.core.domain.content.PackageType;
import org.rhq.core.domain.content.PackageVersion;
import org.rhq.core.domain.content.composite.LoadedPackageBitsComposite;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.util.MessageDigestGenerator;
import org.rhq.enterprise.server.content.ContentManagerLocal;
import org.rhq.enterprise.server.content.ContentUIManagerLocal;
import org.rhq.enterprise.server.test.AbstractEJB3Test;
import org.rhq.enterprise.server.test.TransactionCallback;
import org.rhq.enterprise.server.util.LookupUtil;
import org.rhq.enterprise.server.util.SessionTestHelper;

/**
 * Test case for general {@link org.rhq.enterprise.server.content.ContentUIManagerBean} tests. Any tests that would
 * require a large DB state prior to running are split off into their own test classes.
 *
 * @author Jason Dobies
 */
public class ContentUIManagerBeanTest extends AbstractEJB3Test {

    private static final boolean ENABLE_TESTS = true;

    private ContentUIManagerLocal contentUIManager;
    private ContentManagerLocal contentManager;

    // Setup  --------------------------------------------

    @Override
    protected void beforeMethod() throws Exception {
        contentUIManager = LookupUtil.getContentUIManager();
        contentManager = LookupUtil.getContentManager();
    }

    // Test Cases  --------------------------------------------

    @Test(enabled = ENABLE_TESTS)
    public void testPackageBits() throws Throwable {

        executeInTransaction(new TransactionCallback() {

            public void execute() throws Exception {
                LoadedPackageBitsComposite composite;

                try {
                    Resource resource = SessionTestHelper.createNewResource(em, "testPkgBitsResource");
                    PackageType pkgType = new PackageType("testPkgBitsPT", resource.getResourceType());
                    org.rhq.core.domain.content.Package pkg = new Package("testPkgBitsP", pkgType);
                    Architecture arch = new Architecture("testPkgArch");
                    PackageVersion pkgVer = new PackageVersion(pkg, "1", arch);

                    em.persist(pkgType);
                    em.persist(pkg);
                    em.persist(arch);
                    em.persist(pkgVer);
                    em.flush();

                    // test that no bits are available right now
                    composite = contentUIManager.getLoadedPackageBitsComposite(pkgVer.getId());
                    assert composite != null;
                    assert composite.getPackageVersionId() == pkgVer.getId();
                    assert composite.getPackageBitsId() == null;
                    assert !composite.isPackageBitsAvailable();
                    assert !composite.isPackageBitsInDatabase();

                    // pretend we loaded the bits, but we stored them somewhere other then the DB
                    PackageBits packageBits = createPackageBits();
                    pkgVer.setPackageBits(packageBits);
                    pkgVer = em.merge(pkgVer);
                    em.flush();

                    // test that the bits are available, but are not stored in the DB
                    composite = contentUIManager.getLoadedPackageBitsComposite(pkgVer.getId());
                    assert composite != null;
                    assert composite.getPackageVersionId() == pkgVer.getId();
                    assert composite.getPackageBitsId() == packageBits.getId();
                    assert composite.isPackageBitsAvailable();
                    assert !composite.isPackageBitsInDatabase();

                    // let's make sure there really is no data in the DB
                    packageBits = em.find(PackageBits.class, packageBits.getId());
                    assert packageBits != null;
                    assert packageBits.getBlob().getBits() == null;

                    // now lets store some bits in the DB
                    final String DATA = "testPackageBits data";
                    PackageBitsBlob packageBitsBlob = em.find(PackageBitsBlob.class, packageBits.getId());
                    packageBitsBlob.setBits(DATA.getBytes());
                    em.merge(packageBitsBlob);
                    em.flush();

                    // test that the bits are available and stored in the DB
                    composite = contentUIManager.getLoadedPackageBitsComposite(pkgVer.getId());
                    assert composite != null;
                    assert composite.getPackageVersionId() == pkgVer.getId();
                    assert composite.getPackageBitsId() == packageBits.getId();
                    assert composite.isPackageBitsAvailable();
                    assert composite.isPackageBitsInDatabase();

                    // let's make sure the data really is in the DB
                    packageBits = em.find(PackageBits.class, packageBits.getId());
                    assert packageBits != null;
                    assert DATA.equals(new String(packageBits.getBlob().getBits()));

                    ////////////////////////////////////////////////////
                    // create another package version and test with that
                    ////////////////////////////////////////////////////
                    PackageVersion pkgVer2 = new PackageVersion(pkg, "2", arch);
                    em.persist(pkgVer2);
                    em.flush();

                    // first make sure the query still gets the right answer for the first pkgVer
                    composite = contentUIManager.getLoadedPackageBitsComposite(pkgVer.getId());
                    assert composite != null;
                    assert composite.getPackageVersionId() == pkgVer.getId();
                    assert composite.getPackageBitsId() == packageBits.getId();
                    assert composite.isPackageBitsAvailable();
                    assert composite.isPackageBitsInDatabase();

                    // test that no bits are available right now
                    composite = contentUIManager.getLoadedPackageBitsComposite(pkgVer2.getId());
                    assert composite != null;
                    assert composite.getPackageVersionId() == pkgVer2.getId();
                    assert composite.getPackageBitsId() == null;
                    assert !composite.isPackageBitsAvailable();
                    assert !composite.isPackageBitsInDatabase();

                    // pretend we loaded the bits, but we stored them somewhere other then the DB
                    PackageBits packageBits2 = createPackageBits();
                    pkgVer2.setPackageBits(packageBits2);
                    pkgVer2 = em.merge(pkgVer2);
                    em.flush();

                    // make sure the query still gets the right answer for the first pkgVer
                    composite = contentUIManager.getLoadedPackageBitsComposite(pkgVer.getId());
                    assert composite != null;
                    assert composite.getPackageVersionId() == pkgVer.getId();
                    assert composite.getPackageBitsId() == packageBits.getId();
                    assert composite.isPackageBitsAvailable();
                    assert composite.isPackageBitsInDatabase();

                    // test that the bits are available, but are not stored in the DB
                    composite = contentUIManager.getLoadedPackageBitsComposite(pkgVer2.getId());
                    assert composite != null;
                    assert composite.getPackageVersionId() == pkgVer2.getId();
                    assert composite.getPackageBitsId() == packageBits2.getId();
                    assert composite.isPackageBitsAvailable();
                    assert !composite.isPackageBitsInDatabase();

                    // let's make sure there really is no data in the DB
                    packageBits2 = em.find(PackageBits.class, packageBits2.getId());
                    assert packageBits2 != null;
                    assert packageBits2.getBlob().getBits() == null;

                    // now lets store some bits in the DB
                    final String DATA2 = "testPackageBits more data";
                    packageBits2.getBlob().setBits(DATA2.getBytes());
                    em.merge(packageBits2.getBlob());
                    em.flush();

                    // make sure the query still gets the right answer for the first pkgVer
                    composite = contentUIManager.getLoadedPackageBitsComposite(pkgVer.getId());
                    assert composite != null;
                    assert composite.getPackageVersionId() == pkgVer.getId();
                    assert composite.getPackageBitsId() == packageBits.getId();
                    assert composite.isPackageBitsAvailable();
                    assert composite.isPackageBitsInDatabase();

                    // test that the bits are available and stored in the DB
                    composite = contentUIManager.getLoadedPackageBitsComposite(pkgVer2.getId());
                    assert composite != null;
                    assert composite.getPackageVersionId() == pkgVer2.getId();
                    assert composite.getPackageBitsId() == packageBits2.getId();
                    assert composite.isPackageBitsAvailable();
                    assert composite.isPackageBitsInDatabase();

                    // let's make sure the data really is in the DB
                    packageBits2 = em.find(PackageBits.class, packageBits2.getId());
                    assert packageBits2 != null;
                    assert DATA2.equals(new String(packageBits2.getBlob().getBits()));

                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new RuntimeException(t);
                }
            }
        });
    }

    @Test(enabled = ENABLE_TESTS)
    public void testPackageBitsBlobStream() throws Throwable {

        executeInTransaction(new TransactionCallback() {

            public void execute() throws Exception {
                LoadedPackageBitsComposite composite;
                try {
                    Resource resource = SessionTestHelper.createNewResource(em, "testPkgBitsLargeResource");
                    PackageType pkgType = new PackageType("testPkgBitsLargePT", resource.getResourceType());
                    org.rhq.core.domain.content.Package pkg = new Package("testPkgBitsLargeP", pkgType);
                    Architecture arch = new Architecture("testPkgLargeArch");
                    PackageVersion pkgVer = new PackageVersion(pkg, "1", arch);

                    em.persist(pkgType);
                    em.persist(pkg);
                    em.persist(arch);
                    em.persist(pkgVer);
                    em.flush();

                    // test that no bits are available right now
                    composite = contentUIManager.getLoadedPackageBitsComposite(pkgVer.getId());
                    assert composite != null;
                    assert composite.getPackageVersionId() == pkgVer.getId();
                    assert composite.getPackageBitsId() == null;
                    assert !composite.isPackageBitsAvailable();
                    assert !composite.isPackageBitsInDatabase();

                    // pretend we loaded the bits, but we stored them somewhere other then the DB
                    PackageBits packageBits = createPackageBits();
                    pkgVer.setPackageBits(packageBits);
                    pkgVer = em.merge(pkgVer);
                    em.flush();

                    // test that the bits are available, but are not stored in the DB
                    composite = contentUIManager.getLoadedPackageBitsComposite(pkgVer.getId());
                    assert composite != null;
                    assert composite.getPackageVersionId() == pkgVer.getId();
                    assert composite.getPackageBitsId() == packageBits.getId();
                    assert composite.isPackageBitsAvailable();
                    assert !composite.isPackageBitsInDatabase();

                    // let's make sure there really is no data in the DB
                    packageBits = em.find(PackageBits.class, packageBits.getId());
                    assert packageBits != null;
                    assert packageBits.getBlob().getBits() == null;

                    // now lets store some bits in the DB using PreparedStatements and BLOB mechanism
                    // to simulate large file transfers where streaming is used instead of reading entire
                    // contents into memory every time.

                    // destination once pulled from db
                    File tempDir = getTempDir();
                    if (!tempDir.exists()) {
                        assertTrue("Unable to mkdirs " + tempDir + " for test.", tempDir.mkdirs());
                    }
                    File retrieved = new File(tempDir, "pulled.jar");
                    if (retrieved.exists()) {
                        assertTrue("Unable to delete " + retrieved.getPath() + " for test cleanup.", retrieved.delete());
                    }

                    //any jar should be fine. Use canned jar
                    InputStream originalBinaryStream = this.getClass().getClassLoader()
                        .getResourceAsStream("binary-blob-sample.jar");
                    String originalDigest = new MessageDigestGenerator(MessageDigestGenerator.SHA_256)
                        .calcDigestString(originalBinaryStream);
                    originalBinaryStream.close();
                    originalBinaryStream = this.getClass().getClassLoader()
                        .getResourceAsStream("binary-blob-sample.jar");
                    contentManager.updateBlobStream(originalBinaryStream, packageBits, null);
                    packageBits = em.find(PackageBits.class, packageBits.getId());

                    // test that the bits are available and stored in the DB: Reading the Blob
                    composite = contentUIManager.getLoadedPackageBitsComposite(pkgVer.getId());
                    assert composite != null;
                    assert composite.getPackageVersionId() == pkgVer.getId();
                    assert composite.getPackageBitsId() == packageBits.getId();
                    assert composite.isPackageBitsAvailable();
                    assert composite.isPackageBitsInDatabase();

                    FileOutputStream outputStream = new FileOutputStream(retrieved);
                    contentManager.writeBlobOutToStream(outputStream, packageBits, true);

                    //Check that db content equal to file system content
                    String newDigest = new MessageDigestGenerator(MessageDigestGenerator.SHA_256)
                        .calcDigestString(retrieved);
                    assertEquals("Uploaded and retrieved digests differ:", originalDigest, newDigest);

                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new RuntimeException(t);
                }
            }
        });
    }

    private PackageBits createPackageBits() {
        PackageBits bits = null;
        PackageBitsBlob blob = null;

        // We have to work backwards to avoid constraint violations. PackageBits requires a PackageBitsBlob,
        // so create and persist that first, getting the ID
        blob = new PackageBitsBlob();
        em.persist(blob);

        // Now create the PackageBits entity and assign the Id and blob.  Note, do not persist the
        // entity, the row already exists. Just perform and flush the update.
        bits = new PackageBits();
        bits.setId(blob.getId());
        bits.setBlob(blob);
        em.flush();

        // return the new PackageBits and associated PackageBitsBlob
        return bits;
    }

}