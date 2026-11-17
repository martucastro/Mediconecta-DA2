import 'reflect-metadata';
import { Module } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { RepositorioDeRecordatorios } from './datos/repositorio-de-recordatorios';
import { ServicioDeRecordatorios } from './negocio/servicio-de-recordatorios';
import { RecordatoriosController } from './presentacion/recordatorios.controller';
import { SuscriptorTurnoConfirmado } from './presentacion/suscriptor-turno-confirmado';

@Module({
  controllers: [RecordatoriosController],
  providers: [RepositorioDeRecordatorios, ServicioDeRecordatorios, SuscriptorTurnoConfirmado],
})
class RecordatoriosModule {}

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(RecordatoriosModule);
  app.enableShutdownHooks();
  // ponytail: solo localhost, la API no tiene autenticacion (servicio interno de demo).
  await app.listen(Number(process.env.PORT ?? 3000), '127.0.0.1');
}

void bootstrap();
