import type {Customer,LedgerEntry,LedgerStorage} from './contracts';
/** Phase 2 interface only. Native SQLite operations are implemented in Phase 3. */
export class AndroidLedgerStorage implements LedgerStorage{
 async initialize():Promise<void>{throw new Error('Native SQLite adapter not implemented (Phase 3)')}
 async listCustomers():Promise<Customer[]>{throw new Error('Native SQLite adapter not implemented (Phase 3)')}
 async listEntries(_customerId:string):Promise<LedgerEntry[]>{throw new Error('Native SQLite adapter not implemented (Phase 3)')}
}
