package com.polaroid.app.registros.banco

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.polaroid.app.registros.FormaPagamento
import com.polaroid.app.registros.Registro
import com.polaroid.app.registros.RepositorioRegistros
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Uma tabela para compras, vendas e perdas: facilita somar por período nos relatórios.
 * [valorCentavos] = valor pago (compra) ou preço por foto (venda); null na perda.
 */
@Entity(tableName = "registros", indices = [Index("dataHoraMillis")])
data class RegistroEntidade(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val tipo: String,
    val dataHoraMillis: Long,
    val quantidadeFotos: Int,
    val valorCentavos: Long?,
    val formaPagamento: String?,
    val taxaPontosBase: Int?,
)

@Dao
interface RegistroDao {
    @Query("SELECT * FROM registros ORDER BY dataHoraMillis DESC")
    fun observar(): Flow<List<RegistroEntidade>>

    @Upsert
    suspend fun salvar(registro: RegistroEntidade)

    @Query("DELETE FROM registros WHERE id = :id")
    suspend fun apagar(id: Long)
}

@Database(entities = [RegistroEntidade::class], version = 1)
abstract class BancoPolaroid : RoomDatabase() {
    abstract fun registros(): RegistroDao

    companion object {
        @Volatile
        private var instancia: BancoPolaroid? = null

        fun obter(context: Context): BancoPolaroid = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(context.applicationContext, BancoPolaroid::class.java, "polaroid.db")
                .build()
                .also { instancia = it }
        }
    }
}

class RepositorioRegistrosRoom(private val dao: RegistroDao) : RepositorioRegistros {
    override fun observar(): Flow<List<Registro>> = dao.observar().map { lista -> lista.map { it.paraRegistro() } }

    override suspend fun salvar(registro: Registro) = dao.salvar(registro.paraEntidade())

    override suspend fun apagar(registro: Registro) = dao.apagar(registro.id)
}

fun Registro.paraEntidade(): RegistroEntidade {
    val millis = dataHora.toEpochMilli()
    return when (this) {
        is Registro.Compra -> RegistroEntidade(id, "COMPRA", millis, quantidadeFotos, valorPagoCentavos, null, null)
        is Registro.Venda -> RegistroEntidade(
            id, "VENDA", millis, quantidadeFotos, precoPorFotoCentavos, formaPagamento.name, taxaPontosBase,
        )
        is Registro.Perda -> RegistroEntidade(id, "PERDA", millis, quantidadeFotos, null, null, null)
    }
}

fun RegistroEntidade.paraRegistro(): Registro {
    val instante = Instant.ofEpochMilli(dataHoraMillis)
    return when (tipo) {
        "COMPRA" -> Registro.Compra(id, instante, quantidadeFotos, valorCentavos ?: 0)
        "VENDA" -> Registro.Venda(
            id, instante, quantidadeFotos, valorCentavos ?: 0,
            FormaPagamento.valueOf(formaPagamento ?: FormaPagamento.PIX.name),
            taxaPontosBase ?: 0,
        )
        else -> Registro.Perda(id, instante, quantidadeFotos)
    }
}
