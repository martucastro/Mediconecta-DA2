import { BadRequestException, Controller, Get, ParseIntPipe, Query } from '@nestjs/common';
import { Recordatorio } from '../datos/repositorio-de-recordatorios';
import { ServicioDeRecordatorios } from '../negocio/servicio-de-recordatorios';

@Controller('recordatorios')
export class RecordatoriosController {
  constructor(private readonly servicio: ServicioDeRecordatorios) {}

  /** GET /recordatorios y GET /recordatorios?turnoId=5 */
  @Get()
  listar(
    @Query('turnoId', new ParseIntPipe({
      optional: true,
      exceptionFactory: () => new BadRequestException('turnoId debe ser un numero entero'),
    })) turnoId?: number,
  ): Recordatorio[] {
    return this.servicio.listar(turnoId);
  }
}
