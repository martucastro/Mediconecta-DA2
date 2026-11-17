import { Logger } from '@nestjs/common';
import { EventContext } from 'rhea';
import { ServicioDeRecordatorios } from '../negocio/servicio-de-recordatorios';
import { leerTurnoConfirmado, SuscriptorTurnoConfirmado } from './suscriptor-turno-confirmado';

Logger.overrideLogger(false);

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

describe('SuscriptorTurnoConfirmado: liquidacion del mensaje', () => {
  const registrar = jest.fn();
  const suscriptor = new SuscriptorTurnoConfirmado({ registrar } as unknown as ServicioDeRecordatorios);

  function recibir(body: unknown) {
    const delivery = { accept: jest.fn(), release: jest.fn() };
    // alRecibir es privado: es el handler del evento 'message' de rhea.
    suscriptor['alRecibir']({ message: { body }, delivery } as unknown as EventContext);
    return delivery;
  }

  beforeEach(() => registrar.mockReset());

  it('un mensaje ilegible se acepta (se descarta) sin registrar nada', () => {
    const delivery = recibir('basura');
    expect(registrar).not.toHaveBeenCalled();
    expect(delivery.accept).toHaveBeenCalled();
    expect(delivery.release).not.toHaveBeenCalled();
  });

  it('un evento registrado se acepta', () => {
    const delivery = recibir({ turnoId: 5, pacienteId: 3, fechaHora: '2027-01-15T10:00' });
    expect(registrar).toHaveBeenCalledWith({ turnoId: 5, pacienteId: 3, fechaHora: '2027-01-15T10:00' });
    expect(delivery.accept).toHaveBeenCalled();
    expect(delivery.release).not.toHaveBeenCalled();
  });

  it('si registrar falla, se libera para que Artemis lo reentregue', () => {
    registrar.mockImplementation(() => {
      throw new Error('disco lleno');
    });
    const delivery = recibir({ turnoId: 5, pacienteId: 3, fechaHora: '2027-01-15T10:00' });
    expect(delivery.release).toHaveBeenCalledWith({ delivery_failed: true });
    expect(delivery.accept).not.toHaveBeenCalled();
  });
});
