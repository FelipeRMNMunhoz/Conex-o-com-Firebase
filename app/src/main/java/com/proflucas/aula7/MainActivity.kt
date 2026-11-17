package com.proflucas.aula7

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*

class MainActivity : Activity() {
    private data class Resultado(val id: String, val nome: String, val pontos: Int)

    private var pontuacaoAtual = 0
    private var ranking: DatabaseReference? = null
    private var listener: ValueEventListener? = null
    private var ocupado = false
    private lateinit var textoPontos: TextView
    private lateinit var status: TextView
    private lateinit var nome: EditText
    private lateinit var lista: LinearLayout
    private lateinit var salvar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textoPontos = findViewById(R.id.pontos)
        status = findViewById(R.id.status)
        nome = findViewById(R.id.nome)
        lista = findViewById(R.id.listaRanking)
        salvar = findViewById(R.id.salvar)

        pontuacaoAtual = savedInstanceState?.getInt("pontos")?.coerceAtLeast(0) ?: 0
        mostrarPontos()
        findViewById<Button>(R.id.aumentar).setOnClickListener {
            if (pontuacaoAtual < Int.MAX_VALUE) pontuacaoAtual++
            mostrarPontos()
        }
        findViewById<Button>(R.id.diminuir).setOnClickListener {
            if (pontuacaoAtual > 0) pontuacaoAtual--
            mostrarPontos()
        }
        salvar.setOnClickListener { salvarPontuacao() }
        conectarFirebase()
    }

    private fun mostrarPontos() {
        textoPontos.text = getString(R.string.pontos_atuais, pontuacaoAtual)
    }

    private fun conectarFirebase() {
        try {
            val app = FirebaseApp.getApps(this).firstOrNull()
            val url = getString(R.string.firebase_database_url).trim()
            if (app == null || url.isEmpty()) {
                salvar.isEnabled = false
                status.setText(R.string.configurar_firebase)
                return
            }
            ranking = FirebaseDatabase.getInstance(app, url).getReference("pontuacoes")
            salvar.isEnabled = true
            listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val resultados = snapshot.children.mapNotNull { item ->
                        val id = item.key ?: return@mapNotNull null
                        val jogador = item.child("nome").value as? String ?: return@mapNotNull null
                        val pontos = (item.child("pontos").value as? Number)?.toLong()
                            ?: return@mapNotNull null
                        if (pontos !in 0..Int.MAX_VALUE.toLong()) return@mapNotNull null
                        Resultado(id, jogador, pontos.toInt())
                    }.sortedByDescending { it.pontos }
                    lista.removeAllViews()
                    if (resultados.isEmpty()) lista.addView(TextView(this@MainActivity).apply {
                        setText(R.string.ranking_vazio)
                    })
                    resultados.forEach { resultado ->
                        lista.addView(Button(this@MainActivity).apply {
                            isAllCaps = false
                            text = getString(R.string.item_ranking, resultado.nome, resultado.pontos)
                            setOnClickListener { if (!ocupado) editarOuApagar(resultado) }
                        })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    salvar.isEnabled = false
                    status.text = error.message
                }
            }
            ranking!!.addValueEventListener(listener!!)
        } catch (e: Exception) {
            salvar.isEnabled = false
            status.text = e.localizedMessage
        }
    }

    private fun salvarPontuacao() {
        val jogador = nome.text.toString().trim()
        if (jogador.isEmpty()) {
            nome.error = getString(R.string.nome_obrigatorio)
            return
        }
        val registro = ranking?.push() ?: return
        ocupado = true
        salvar.isEnabled = false
        registro.setValue(mapOf("nome" to jogador, "pontos" to pontuacaoAtual))
            .addOnSuccessListener { finalizar(getString(R.string.salvo)) }
            .addOnFailureListener { finalizar(it.localizedMessage ?: getString(R.string.erro)) }
    }

    private fun editarOuApagar(resultado: Resultado) {
        val formulario = layoutInflater.inflate(R.layout.dialog_editar, null)
        formulario.findViewById<TextView>(R.id.idSelecionado).text =
            getString(R.string.id_registro, resultado.id)
        val editarNome = formulario.findViewById<EditText>(R.id.editarNome)
        val editarPontos = formulario.findViewById<EditText>(R.id.editarPontos)
        editarNome.setText(resultado.nome)
        editarPontos.setText(resultado.pontos.toString())

        val dialogo = AlertDialog.Builder(this)
            .setTitle(R.string.editar_resultado)
            .setView(formulario)
            .setPositiveButton(R.string.corrigir, null)
            .setNeutralButton(R.string.apagar, null)
            .setNegativeButton(R.string.cancelar, null)
            .create()
        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val novoNome = editarNome.text.toString().trim()
                val novosPontos = editarPontos.text.toString().toIntOrNull()
                when {
                    novoNome.isEmpty() -> editarNome.error = getString(R.string.nome_obrigatorio)
                    novosPontos == null || novosPontos < 0 ->
                        editarPontos.error = getString(R.string.pontos_invalidos)
                    else -> {
                        dialogo.dismiss()
                        modificarRegistro(resultado.id, novoNome, novosPontos)
                    }
                }
            }
            dialogo.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialogo.dismiss()
                AlertDialog.Builder(this)
                    .setTitle(R.string.confirmar_exclusao)
                    .setMessage(getString(R.string.confirmar_item, resultado.nome))
                    .setNegativeButton(R.string.cancelar, null)
                    .setPositiveButton(R.string.apagar) { _, _ -> modificarRegistro(resultado.id) }
                    .show()
            }
        }
        dialogo.show()
    }

    // Modifica somente pontuacoes/{id}, nunca a lista inteira.
    private fun modificarRegistro(id: String, jogador: String? = null, pontos: Int = 0) {
        val registro = ranking?.child(id) ?: return
        ocupado = true
        salvar.isEnabled = false
        registro.get().addOnSuccessListener { atual ->
            if (!atual.exists()) {
                finalizar(getString(R.string.inexistente))
                return@addOnSuccessListener
            }
            val operacao = if (jogador == null) registro.removeValue()
            else registro.updateChildren(mapOf("nome" to jogador, "pontos" to pontos))
            operacao.addOnSuccessListener {
                finalizar(getString(if (jogador == null) R.string.apagado else R.string.corrigido))
            }.addOnFailureListener { finalizar(it.localizedMessage ?: getString(R.string.erro)) }
        }.addOnFailureListener { finalizar(it.localizedMessage ?: getString(R.string.erro)) }
    }

    private fun finalizar(mensagem: String) {
        ocupado = false
        salvar.isEnabled = ranking != null
        status.text = mensagem
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("pontos", pontuacaoAtual)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        listener?.let { ranking?.removeEventListener(it) }
        super.onDestroy()
    }
}
