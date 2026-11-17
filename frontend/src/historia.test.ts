import { describe, expect, it } from 'vitest'
import { armarConsulta, cuerpo, ordenarRecientes, pacienteDeLaPagina, titulo, type EntradaDTO } from './historia'

function entrada(id: number, fecha: string, extra: Partial<EntradaDTO> = {}): EntradaDTO {
  return { id, tipo: 'ANTECEDENTE', profesionalId: 2, fecha, ...extra }
}

describe('ordenarRecientes', () => {
  it('pone primero la entrada mas reciente', () => {
    const entradas = [
      entrada(1, '2026-03-01T10:00:00'),
      entrada(2, '2026-11-17T09:30:00.123456'),
      entrada(3, '2026-06-15T18:00:00'),
    ]

    expect(ordenarRecientes(entradas).map((e) => e.id)).toEqual([2, 3, 1])
  })

  it('con la misma fecha, la de id mayor (la ultima guardada) va primero', () => {
    // Una consulta guarda el diagnostico y sus recetas en el mismo instante.
    const entradas = [entrada(4, '2026-11-17T09:30:00'), entrada(5, '2026-11-17T09:30:00')]

    expect(ordenarRecientes(entradas).map((e) => e.id)).toEqual([5, 4])
  })

  it('no modifica la lista original', () => {
    const entradas = [entrada(1, '2026-01-01T10:00:00'), entrada(2, '2026-02-01T10:00:00')]

    ordenarRecientes(entradas)

    expect(entradas.map((e) => e.id)).toEqual([1, 2])
  })
})

describe('titulo y cuerpo', () => {
  it('un diagnostico muestra la descripcion y el codigo CIE-10', () => {
    const diagnostico = entrada(1, '2026-11-17T09:30:00', {
      tipo: 'DIAGNOSTICO', codigoCIE10: 'J00', descripcion: 'Resfrío común',
    })

    expect(titulo(diagnostico)).toBe('Resfrío común · CIE-10 J00')
    expect(cuerpo(diagnostico)).toBe('')
  })

  it('una receta muestra medicamento, dosis y dias de tratamiento', () => {
    const receta = entrada(2, '2026-11-17T09:30:00', {
      tipo: 'RECETA', medicamento: 'Paracetamol', dosis: '500 mg', diasTratamiento: 5,
    })

    expect(titulo(receta)).toBe('Paracetamol · 500 mg')
    expect(cuerpo(receta)).toBe('Tratamiento por 5 días.')
  })

  it('un antecedente muestra su tipo y su detalle', () => {
    const antecedente = entrada(3, '2026-11-17T09:30:00', { tipoAntecedente: 'Alergia', detalle: 'Penicilina' })

    expect(titulo(antecedente)).toBe('Alergia')
    expect(cuerpo(antecedente)).toBe('Penicilina')
  })
})

describe('armarConsulta', () => {
  it('arma el cuerpo de POST /consultas con el diagnostico y las recetas', () => {
    const cuerpoPedido = armarConsulta(' J00 ', ' Resfrío común ', [
      { medicamento: ' Paracetamol ', dosis: '500 mg', dias: '5' },
    ])

    expect(cuerpoPedido).toEqual({
      diagnostico: { codigoCIE10: 'J00', descripcion: 'Resfrío común' },
      recetas: [{ medicamento: 'Paracetamol', dosis: '500 mg', diasTratamiento: 5 }],
    })
  })

  it('manda los campos vacios como null para que el backend diga cual falta', () => {
    const cuerpoPedido = armarConsulta('', '  ', [{ medicamento: '', dosis: '1 cada 8 h', dias: '' }])

    expect(cuerpoPedido).toEqual({
      diagnostico: { codigoCIE10: null, descripcion: null },
      recetas: [{ medicamento: null, dosis: '1 cada 8 h', diasTratamiento: null }],
    })
  })

  it('sin recetas manda una lista vacia', () => {
    expect(armarConsulta('J00', 'Resfrío común', []).recetas).toEqual([])
  })
})

describe('pacienteDeLaPagina', () => {
  it('el profesional ve el paciente que pide la URL', () => {
    expect(pacienteDeLaPagina('PROFESIONAL', 2, '3')).toBe(3)
  })

  it('el profesional sin paciente en la URL no ve ninguno', () => {
    expect(pacienteDeLaPagina('PROFESIONAL', 2, null)).toBeNull()
  })

  it('el paciente ve la suya si la URL no pide otra', () => {
    expect(pacienteDeLaPagina('PACIENTE', 3, null)).toBe(3)
  })

  it('el paciente que pide otra historia la pide igual: el backend responde 403', () => {
    expect(pacienteDeLaPagina('PACIENTE', 3, '5')).toBe(5)
  })

  it('un id que no es un numero entero positivo se descarta', () => {
    expect(pacienteDeLaPagina('PROFESIONAL', 2, 'abc')).toBeNull()
    expect(pacienteDeLaPagina('PROFESIONAL', 2, '-1')).toBeNull()
    expect(pacienteDeLaPagina('PROFESIONAL', 2, '0')).toBeNull()
  })

  it('el paciente con un id malformado en la URL ve la suya, no el aviso para profesionales', () => {
    expect(pacienteDeLaPagina('PACIENTE', 3, 'abc')).toBe(3)
    expect(pacienteDeLaPagina('PACIENTE', 3, '0')).toBe(3)
    expect(pacienteDeLaPagina('PACIENTE', 3, '')).toBe(3)
  })
})
