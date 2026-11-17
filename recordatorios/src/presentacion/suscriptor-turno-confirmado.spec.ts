import { leerTurnoConfirmado } from './suscriptor-turno-confirmado';

describe('leerTurnoConfirmado', () => {
  it('traduce el MapMessage que publica ServicioDeTurnos', () => {
    const cuerpo = { turnoId: 5, pacienteId: 3, profesionalId: 2, fechaHora: '2027-01-15T10:00' };
    expect(leerTurnoConfirmado(cuerpo)).toEqual({ turnoId: 5, pacienteId: 3, fechaHora: '2027-01-15T10:00' });
  });

  it.each([
    ['sin cuerpo', undefined],
    ['un texto', 'TurnoConfirmado'],
    ['sin turnoId', { pacienteId: 3, fechaHora: '2027-01-15T10:00' }],
    ['turnoId no numerico', { turnoId: '5', pacienteId: 3, fechaHora: '2027-01-15T10:00' }],
    ['fecha ilegible', { turnoId: 5, pacienteId: 3, fechaHora: 'manana' }],
  ])('descarta un mensaje %s', (_caso, cuerpo) => {
    expect(leerTurnoConfirmado(cuerpo)).toBeNull();
  });
});
