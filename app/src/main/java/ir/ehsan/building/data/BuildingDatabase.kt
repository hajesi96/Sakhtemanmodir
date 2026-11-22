package ir.ehsan.building.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "units")
data class UnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: Int,
    val ownerName: String = "",
    val tenantName: String = "",
    val phone: String = "",
    val peopleCount: Int = 0,
    val fixedCharge: Long = 0,
    val note: String = ""
)

@Entity(tableName = "residents", foreignKeys = [ForeignKey(entity = UnitEntity::class, parentColumns = ["id"], childColumns = ["unitId"], onDelete = ForeignKey.CASCADE)], indices = [Index("unitId")])
data class ResidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unitId: Long,
    val name: String,
    val phone: String = ""
)

@Entity(tableName = "payments", indices = [Index("unitId"), Index("monthKey")])
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unitId: Long,
    val amount: Long,
    val monthKey: String,
    val dateText: String,
    val description: String = ""
)

@Entity(tableName = "expenses", indices = [Index("monthKey")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val amount: Long,
    val monthKey: String,
    val dateText: String,
    val description: String = ""
)

@Entity(tableName = "water_bills", primaryKeys = ["monthKey"])
data class WaterBillEntity(val monthKey: String, val totalAmount: Long, val savedAt: Long = System.currentTimeMillis())

@Entity(tableName = "water_allocations", primaryKeys = ["monthKey", "unitId"], indices = [Index("unitId")])
data class WaterAllocationEntity(val monthKey: String, val unitId: Long, val residentCount: Int, val amount: Long)

@Entity(tableName = "accounts", indices = [Index(value = ["username"], unique = true), Index("unitId")])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val passwordSalt: String,
    val passwordHash: String,
    val role: String,
    val unitId: Long? = null,
    val active: Boolean = true
)

@Dao
interface BuildingDao {
    @Query("SELECT COUNT(*) FROM accounts WHERE role = 'manager'") suspend fun managerCount(): Int
    @Query("SELECT * FROM accounts WHERE username = :username LIMIT 1") suspend fun accountByUsername(username: String): AccountEntity?
    @Query("SELECT * FROM accounts ORDER BY id") fun accounts(): Flow<List<AccountEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAccount(account: AccountEntity)
    @Query("DELETE FROM accounts WHERE id = :id AND role = 'resident'") suspend fun deleteResidentAccount(id: Long)
    @Query("SELECT * FROM units ORDER BY number") fun units(): Flow<List<UnitEntity>>
    @Query("SELECT * FROM residents ORDER BY unitId, name") fun residents(): Flow<List<ResidentEntity>>
    @Query("SELECT * FROM payments ORDER BY id DESC") fun payments(): Flow<List<PaymentEntity>>
    @Query("SELECT * FROM expenses ORDER BY id DESC") fun expenses(): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM water_bills ORDER BY monthKey DESC") fun waterBills(): Flow<List<WaterBillEntity>>
    @Query("SELECT * FROM water_allocations WHERE monthKey = :month ORDER BY unitId") suspend fun waterAllocations(month: String): List<WaterAllocationEntity>
    @Query("SELECT * FROM water_bills WHERE monthKey = :month LIMIT 1") suspend fun waterBill(month: String): WaterBillEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveWaterBill(item: WaterBillEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveWaterAllocations(items: List<WaterAllocationEntity>)
    @Query("DELETE FROM water_allocations WHERE monthKey = :month") suspend fun clearWaterAllocations(month: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertUnit(item: UnitEntity)
    @Insert suspend fun insertResident(item: ResidentEntity)
    @Insert suspend fun insertPayment(item: PaymentEntity)
    @Insert suspend fun insertExpense(item: ExpenseEntity)
    @Query("SELECT COUNT(*) FROM units") suspend fun unitCount(): Int
    @Query("SELECT * FROM units ORDER BY number") suspend fun allUnits(): List<UnitEntity>
    @Query("SELECT * FROM residents ORDER BY unitId, name") suspend fun allResidents(): List<ResidentEntity>
    @Query("SELECT * FROM payments WHERE monthKey = :month ORDER BY id") suspend fun paymentsForMonth(month: String): List<PaymentEntity>
    @Query("SELECT * FROM expenses WHERE monthKey = :month ORDER BY id") suspend fun expensesForMonth(month: String): List<ExpenseEntity>
    @Query("SELECT COUNT(*) FROM residents WHERE unitId = :unitId") suspend fun residentCount(unitId: Long): Int
    @Query("DELETE FROM units WHERE id = :id") suspend fun deleteUnit(id: Long)
    @Query("DELETE FROM residents WHERE id = :id") suspend fun deleteResident(id: Long)
    @Query("DELETE FROM payments WHERE id = :id") suspend fun deletePayment(id: Long)
    @Query("DELETE FROM expenses WHERE id = :id") suspend fun deleteExpense(id: Long)
}

@Database(entities = [UnitEntity::class, ResidentEntity::class, PaymentEntity::class, ExpenseEntity::class, WaterBillEntity::class, WaterAllocationEntity::class, AccountEntity::class], version = 4, exportSchema = false)
abstract class BuildingDatabase : RoomDatabase() {
    abstract fun dao(): BuildingDao
    companion object {
        @Volatile private var INSTANCE: BuildingDatabase? = null
        fun get(context: Context): BuildingDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, BuildingDatabase::class.java, "building-manager.db")
                .addMigrations(object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS water_bills (monthKey TEXT NOT NULL, totalAmount INTEGER NOT NULL, savedAt INTEGER NOT NULL, PRIMARY KEY(monthKey))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS water_allocations (monthKey TEXT NOT NULL, unitId INTEGER NOT NULL, residentCount INTEGER NOT NULL, amount INTEGER NOT NULL, PRIMARY KEY(monthKey, unitId))")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_water_allocations_unitId ON water_allocations(unitId)")
                    }
                }, object : Migration(2, 3) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE units ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
                        db.execSQL("ALTER TABLE units ADD COLUMN peopleCount INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("UPDATE units SET peopleCount = (SELECT COUNT(*) FROM residents WHERE residents.unitId = units.id)")
                        db.execSQL("UPDATE units SET phone = COALESCE((SELECT phone FROM residents WHERE residents.unitId = units.id AND phone != '' ORDER BY id LIMIT 1), '')")
                    }
                }, object : Migration(3, 4) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, username TEXT NOT NULL, passwordSalt TEXT NOT NULL, passwordHash TEXT NOT NULL, role TEXT NOT NULL, unitId INTEGER, active INTEGER NOT NULL DEFAULT 1)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_accounts_username ON accounts(username)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_accounts_unitId ON accounts(unitId)")
                    }
                }).build().also { INSTANCE = it }
        }
    }
}
