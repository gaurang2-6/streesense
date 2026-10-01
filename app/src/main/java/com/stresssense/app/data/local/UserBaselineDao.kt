package com.stresssense.app.data.local

import androidx.room.*
import com.stresssense.app.data.models.UserBaseline
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for user baseline data.
 */
@Dao
interface UserBaselineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseline(baseline: UserBaseline)

    @Query("SELECT * FROM user_baseline WHERE id = 1")
    suspend fun getBaseline(): UserBaseline?

    @Query("SELECT * FROM user_baseline WHERE id = 1")
    fun observeBaseline(): Flow<UserBaseline?>

    @Query("DELETE FROM user_baseline")
    suspend fun deleteBaseline()
}
