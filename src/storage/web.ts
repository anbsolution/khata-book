import {openDB,type IDBPDatabase} from 'idb';
import type {Customer,LedgerEntry,LedgerStorage} from './contracts';
export class WebLedgerStorage implements LedgerStorage{
 private db?:IDBPDatabase;
 async initialize(){this.db=await openDB('anb-khata-book',1,{upgrade(db){if(!db.objectStoreNames.contains('customers'))db.createObjectStore('customers',{keyPath:'id'});if(!db.objectStoreNames.contains('entries'))db.createObjectStore('entries',{keyPath:'id'});}})}
 async listCustomers():Promise<Customer[]>{if(!this.db)throw Error('Database not initialized');return this.db.getAll('customers')}
 async listEntries(customerId:string):Promise<LedgerEntry[]>{if(!this.db)throw Error('Database not initialized');const entries=await this.db.getAll('entries') as LedgerEntry[];return entries.filter(e=>e.customerId===customerId)}
}
