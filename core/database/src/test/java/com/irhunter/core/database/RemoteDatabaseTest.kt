package com.irhunter.core.database

import android.content.Context
import com.irhunter.core.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RemoteDatabaseTest {
    @Test fun createRenameRestoreAndDeleteOnDisk() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("ir-hunter.db")
        val p = RemoteProfile("one", "TV", DeviceCategory.TV, "Unknown Brand", "Unknown Model", "source", "license", "# attribution",
            listOf(RemoteCommand("Off", IrSignal.Raw(intArrayOf(500, 500, 500), 38000), true)), true)
        var db = RemoteDatabase.open(context)
        db.profiles().put(ProfileJson.encode(p))
        val renamed = p.copy(name = "Living room", model = "Edited")
        db.profiles().put(ProfileJson.encode(renamed)); db.close()
        db = RemoteDatabase.open(context)
        assertEquals(renamed, ProfileJson.decode(db.profiles().all().single()))
        db.profiles().delete("one"); assertTrue(db.profiles().all().isEmpty()); db.close()
        context.deleteDatabase("ir-hunter.db")
    }
}
